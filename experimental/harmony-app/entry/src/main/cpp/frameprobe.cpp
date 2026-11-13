#include <napi/native_api.h>
#include <multimedia/player_framework/native_avscreen_capture.h>
#include <multimedia/player_framework/native_avbuffer.h>
#include <native_buffer/native_buffer.h>
#include <native_buffer/buffer_common.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <fstream>
#include <mutex>
#include <sstream>
#include <thread>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
#include <dlfcn.h>
#include <cmath>
#include "HarmonyHost.h"

using Clock = std::chrono::steady_clock;

class Session {
public:
    ~Session() { Stop(); }

    int Start(int width, int height, const std::string &directory)
    {
        Stop();
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || directory.empty()) return -1;
        {
            std::lock_guard<std::mutex> lock(dataMutex);
            pixels.clear();
            frames = copied = mapErrors = formatErrors = 0;
            frameWidth = frameHeight = stride = format = 0;
            lastTimestamp = copyMicros = 0;
            firstFrame = lastFrame = lastCopy = Clock::time_point{};
            outputDir = directory;
        }
        canceled = false;
        started = false;
        ended = false;
        done = false;
        state = -1;
        error = 0;
        stopResult = releaseResult = 0;
        imageSaved = false;
        stage = "create";
        capture = OH_AVScreenCapture_Create();
        if (!capture) return Fail(-2);
        stage = "set_state_callback";
        int result = OH_AVScreenCapture_SetStateCallback(capture, StateChanged, this);
        if (result == 0) { stage = "set_error_callback"; result = OH_AVScreenCapture_SetErrorCallback(capture, ErrorOccurred, this); }
        if (result == 0) { stage = "set_data_callback"; result = OH_AVScreenCapture_SetDataCallback(capture, BufferAvailable, this); }
        OH_AVScreenCaptureConfig config{};
        config.captureMode = OH_CAPTURE_HOME_SCREEN;
        config.dataType = OH_ORIGINAL_STREAM;
        // Zero audio configuration disables both internal audio and the microphone.
        config.videoInfo.videoCapInfo.videoFrameWidth = width;
        config.videoInfo.videoCapInfo.videoFrameHeight = height;
        config.videoInfo.videoCapInfo.videoSource = OH_VIDEO_SOURCE_SURFACE_RGBA;
        if (result == 0) { stage = "init"; result = OH_AVScreenCapture_Init(capture, config); }
        if (result == 0) { stage = "disable_microphone"; result = OH_AVScreenCapture_SetMicrophoneEnabled(capture, false); }
        if (result == 0) { stage = "set_canvas_rotation"; result = OH_AVScreenCapture_SetCanvasRotation(capture, true); }
        auto *strategy = OH_AVScreenCapture_CreateCaptureStrategy();
        if (!strategy && result == 0) result = -3;
        if (strategy) {
            if (result == 0) { stage = "set_rotation_strategy"; result = OH_AVScreenCapture_StrategyForCanvasFollowRotation(strategy, true); }
            if (result == 0) { stage = "set_capture_strategy"; result = OH_AVScreenCapture_SetCaptureStrategy(capture, strategy); }
            OH_AVScreenCapture_ReleaseCaptureStrategy(strategy);
        }
        if (result == 0) { stage = "start_capture"; result = OH_AVScreenCapture_StartScreenCapture(capture); }
        if (result != 0) return Fail(result);
        worker = std::thread([this] {
            std::unique_lock<std::mutex> lock(waitMutex);
            bool signaled = changed.wait_for(lock, std::chrono::seconds(90), [this] {
                return started || ended || canceled;
            });
            if (!signaled) error = -4; // Authorization timeout.
            if (started && !ended && !canceled) {
                lock.unlock();
                stage = "set_frame_rate";
                int result = OH_AVScreenCapture_SetMaxVideoFrameRate(capture, 20);
                if (result != 0) {
                    error = result;
                    ended = true;
                } else {
                    stage = "streaming";
                }
                lock.lock();
                changed.wait_for(lock, std::chrono::seconds(20), [this] { return ended || canceled; });
            }
            lock.unlock();
            Release();
            done = true;
            Save();
        });
        return 0;
    }

    void Stop()
    {
        {
            std::lock_guard<std::mutex> lock(waitMutex);
            canceled = true;
        }
        changed.notify_all();
        if (worker.joinable()) worker.join();
        Release();
    }

    std::string Status()
    {
        std::lock_guard<std::mutex> lock(dataMutex);
        double seconds = frames > 1 ? std::chrono::duration<double>(lastFrame - firstFrame).count() : 0;
        std::ostringstream out;
        out << "{\"stage\":\"" << stage.load() << "\",\"state\":" << state << ",\"error\":" << error
            << ",\"started\":" << (started ? "true" : "false")
            << ",\"ended\":" << (ended ? "true" : "false")
            << ",\"done\":" << (done ? "true" : "false")
            << ",\"frames\":" << frames << ",\"copied\":" << copied
            << ",\"width\":" << frameWidth << ",\"height\":" << frameHeight
            << ",\"stride\":" << stride << ",\"format\":" << format
            << ",\"mapErrors\":" << mapErrors << ",\"formatErrors\":" << formatErrors
            << ",\"timestampNs\":" << lastTimestamp
            << ",\"fps\":" << (seconds > 0 ? (frames - 1) / seconds : 0)
            << ",\"averageCopyUs\":" << (copied ? copyMicros / copied : 0)
            << ",\"stopResult\":" << stopResult << ",\"releaseResult\":" << releaseResult
            << ",\"imageSaved\":" << (imageSaved ? "true" : "false") << "}";
        return out.str();
    }

    std::string MatchSettings()
    {
        std::lock_guard<std::mutex> lock(dataMutex);
        if (pixels.empty() || !started || done || Clock::now() - lastCopy > std::chrono::seconds(1))
            return "{\"ok\":false,\"reason\":\"no fresh frame\"}";
        if (frameWidth != 2232 || frameHeight != 1320)
            return "{\"ok\":false,\"reason\":\"unexpected orientation or size\"}";
        try {
            cv::Mat rgba(frameHeight, frameWidth, CV_8UC4, pixels.data());
            cv::Mat rgb, gray, pattern, scores;
            cv::cvtColor(rgba, rgb, format == NATIVEBUFFER_PIXEL_FMT_BGRA_8888 ? cv::COLOR_BGRA2BGR : cv::COLOR_RGBA2BGR);
            cv::cvtColor(rgb, gray, cv::COLOR_BGR2GRAY);
            pattern = cv::imread(outputDir + "/home-settings.png", cv::IMREAD_GRAYSCALE);
            if (pattern.empty() || pattern.cols != 94 || pattern.rows != 95)
                return "{\"ok\":false,\"reason\":\"template missing or invalid\"}";
            cv::matchTemplate(gray(cv::Rect(0, 0, 180, 150)), pattern, scores, cv::TM_CCOEFF_NORMED);
            double score = 0;
            cv::Point point;
            cv::minMaxLoc(scores, nullptr, &score, nullptr, &point);
            cv::Mat destination = cv::imread(outputDir + "/settings-header.png", cv::IMREAD_GRAYSCALE);
            if (destination.empty() || destination.cols != 230 || destination.rows != 105)
                return "{\"ok\":false,\"reason\":\"destination template missing or invalid\"}";
            cv::Mat destinationScores;
            cv::matchTemplate(gray(cv::Rect(310, 0, 320, 155)), destination, destinationScores, cv::TM_CCOEFF_NORMED);
            double destinationScore = 0;
            cv::minMaxLoc(destinationScores, nullptr, &destinationScore);
            std::ostringstream result;
            result << "{\"ok\":" << (score >= 0.93 ? "true" : "false")
                << ",\"score\":" << score << ",\"x\":" << point.x + pattern.cols / 2
                << ",\"y\":" << point.y + pattern.rows / 2 << ",\"frame\":" << copied
                << ",\"settings\":" << (destinationScore >= 0.93 ? "true" : "false")
                << ",\"settingsScore\":" << destinationScore << "}";
            cv::imwrite(outputDir + "/recognition-frame.png", rgb);
            return result.str();
        } catch (const cv::Exception &) {
            return "{\"ok\":false,\"reason\":\"OpenCV exception\"}";
        }
    }

    bool FrameSize(int *width, int *height)
    {
        std::lock_guard<std::mutex> lock(dataMutex);
        if (!started || done || pixels.empty() || Clock::now() - lastCopy > std::chrono::seconds(1)) return false;
        *width = frameWidth;
        *height = frameHeight;
        return true;
    }

    bool ReadBgr(uint8_t *output, size_t capacity, int width, int height)
    {
        std::lock_guard<std::mutex> lock(dataMutex);
        if (!started || done || pixels.empty() || Clock::now() - lastCopy > std::chrono::seconds(1) ||
            width != frameWidth || height != frameHeight || !output ||
            capacity < static_cast<size_t>(width) * height * 3) return false;
        try {
            cv::Mat rgba(height, width, CV_8UC4, pixels.data());
            cv::Mat bgr(height, width, CV_8UC3, output);
            cv::cvtColor(rgba, bgr, format == NATIVEBUFFER_PIXEL_FMT_BGRA_8888 ? cv::COLOR_BGRA2BGR : cv::COLOR_RGBA2BGR);
            return true;
        } catch (const cv::Exception &) {
            return false;
        }
    }

private:
    std::atomic<const char *> stage{"idle"};
    OH_AVScreenCapture *capture = nullptr;
    std::thread worker;
    std::mutex dataMutex, waitMutex;
    std::condition_variable changed;
    std::atomic<bool> canceled{false}, started{false}, ended{false}, done{true}, imageSaved{false};
    std::atomic<int> state{-1}, error{0}, stopResult{0}, releaseResult{0};
    uint64_t frames = 0, copied = 0, mapErrors = 0, formatErrors = 0;
    int frameWidth = 0, frameHeight = 0, stride = 0, format = 0;
    int64_t lastTimestamp = 0;
    uint64_t copyMicros = 0;
    Clock::time_point firstFrame{}, lastFrame{}, lastCopy{};
    std::vector<uint8_t> pixels;
    std::string outputDir;

    int Fail(int code)
    {
        error = code;
        Release();
        done = true;
        Save();
        return code;
    }

    void Release()
    {
        if (!capture) return;
        if (started) stopResult = OH_AVScreenCapture_StopScreenCapture(capture);
        releaseResult = OH_AVScreenCapture_Release(capture);
        capture = nullptr;
    }

    static void StateChanged(OH_AVScreenCapture *, OH_AVScreenCaptureStateCode code, void *context)
    {
        auto &self = *static_cast<Session *>(context);
        std::lock_guard<std::mutex> lock(self.waitMutex);
        self.state = code;
        if (code == OH_SCREEN_CAPTURE_STATE_STARTED) self.started = true;
        if (code == OH_SCREEN_CAPTURE_STATE_CANCELED || code == OH_SCREEN_CAPTURE_STATE_STOPPED_BY_USER ||
            code == OH_SCREEN_CAPTURE_STATE_INTERRUPTED_BY_OTHER || code == OH_SCREEN_CAPTURE_STATE_STOPPED_BY_CALL ||
            code == OH_SCREEN_CAPTURE_STATE_STOPPED_BY_USER_SWITCHES) self.ended = true;
        self.changed.notify_all();
    }

    static void ErrorOccurred(OH_AVScreenCapture *, int32_t code, void *context)
    {
        auto &self = *static_cast<Session *>(context);
        std::lock_guard<std::mutex> lock(self.waitMutex);
        self.error = code;
        self.ended = true;
        self.changed.notify_all();
    }

    static void BufferAvailable(OH_AVScreenCapture *, OH_AVBuffer *buffer,
        OH_AVScreenCaptureBufferType type, int64_t timestamp, void *context)
    {
        if (type != OH_SCREEN_CAPTURE_BUFFERTYPE_VIDEO) return;
        auto &self = *static_cast<Session *>(context);
        auto now = Clock::now();
        std::lock_guard<std::mutex> lock(self.dataMutex);
        if (self.frames++ == 0) self.firstFrame = now;
        self.lastFrame = now;
        self.lastTimestamp = timestamp;
        if (self.lastCopy != Clock::time_point{} && now - self.lastCopy < std::chrono::milliseconds(190)) return;
        OH_NativeBuffer *native = OH_AVBuffer_GetNativeBuffer(buffer);
        if (!native) { ++self.mapErrors; return; }
        OH_NativeBuffer_Config config{};
        OH_NativeBuffer_GetConfig(native, &config);
        self.format = config.format;
        self.stride = config.stride;
        bool packed = config.format == NATIVEBUFFER_PIXEL_FMT_RGBA_8888 ||
            config.format == NATIVEBUFFER_PIXEL_FMT_RGBX_8888 || config.format == NATIVEBUFFER_PIXEL_FMT_BGRA_8888;
        if (!packed || config.width <= 0 || config.height <= 0 || config.width > 4096 || config.height > 4096 ||
            config.stride < config.width * 4) {
            ++self.formatErrors;
            OH_NativeBuffer_Unreference(native);
            return;
        }
        void *address = nullptr;
        int result = OH_NativeBuffer_Map(native, &address);
        if (result != 0 || !address) {
            ++self.mapErrors;
            OH_NativeBuffer_Unreference(native);
            return;
        }
        size_t rowBytes = static_cast<size_t>(config.width) * 4;
        self.pixels.resize(rowBytes * config.height);
        for (int y = 0; y < config.height; ++y) {
            std::memcpy(self.pixels.data() + rowBytes * y,
                static_cast<uint8_t *>(address) + static_cast<size_t>(config.stride) * y, rowBytes);
        }
        OH_NativeBuffer_Unmap(native);
        OH_NativeBuffer_Unreference(native);
        self.frameWidth = config.width;
        self.frameHeight = config.height;
        self.lastCopy = now;
        ++self.copied;
        self.copyMicros += std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - now).count();
    }

    void Save()
    {
        {
            std::lock_guard<std::mutex> lock(dataMutex);
            if (!pixels.empty()) {
                std::ofstream image(outputDir + "/native-frame.ppm", std::ios::binary | std::ios::trunc);
                image << "P6\n" << frameWidth << " " << frameHeight << "\n255\n";
                std::vector<char> row(static_cast<size_t>(frameWidth) * 3);
                bool bgra = format == NATIVEBUFFER_PIXEL_FMT_BGRA_8888;
                for (int y = 0; y < frameHeight; ++y) {
                    for (int x = 0; x < frameWidth; ++x) {
                        const uint8_t *p = pixels.data() + (static_cast<size_t>(y) * frameWidth + x) * 4;
                        row[x * 3] = static_cast<char>(p[bgra ? 2 : 0]);
                        row[x * 3 + 1] = static_cast<char>(p[1]);
                        row[x * 3 + 2] = static_cast<char>(p[bgra ? 0 : 2]);
                    }
                    image.write(row.data(), static_cast<std::streamsize>(row.size()));
                }
                image.close();
                imageSaved = image.good();
            }
        }
        std::ofstream report(outputDir + "/native-frame-report.json", std::ios::trunc);
        report << Status();
    }
};

static Session session;

// This bridge uses native sockets so MaaCore never waits on the ArkTS UI thread.
static bool InvokeTouch(int descriptor, const std::string &api, const std::string &args, bool create)
{
    const std::string request = "{\"module\":\"com.ohos.devicetest.hypiumApiHelper\","
        "\"method\":\"callHypiumApi\",\"params\":{\"api\":\"" + api +
        "\",\"this\":\"Driver#0\",\"args\":" + args +
        ",\"message_type\":\"hypium\"},\"request_id\":\"maa-native\"}\n";
    size_t offset = 0;
    while (offset < request.size()) {
        ssize_t count = send(descriptor, request.data() + offset, request.size() - offset, MSG_NOSIGNAL);
        if (count <= 0) return false;
        offset += static_cast<size_t>(count);
    }
    std::string response;
    char data[1024];
    while (response.size() < 8192) {
        ssize_t count = recv(descriptor, data, sizeof(data), 0);
        if (count <= 0) return false;
        response.append(data, static_cast<size_t>(count));
        if (response.find("exception") != std::string::npos) return false;
        // The tested local agent emits one compact JSON object without a newline.
        if (response == (create ? "{\"result\":\"Driver#0\"}" : "{\"result\":null}")) return true;
        if (!response.empty() && response.back() == '}') return false;
    }
    return false;
}

static bool NativeTouch(const std::string &api, const std::string &args, int width, int height)
{
    int currentWidth = 0, currentHeight = 0;
    if (!session.FrameSize(&currentWidth, &currentHeight) || width != currentWidth || height != currentHeight) return false;
    int descriptor = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (descriptor < 0) return false;
    timeval timeout{5, 0};
    setsockopt(descriptor, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(descriptor, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_port = htons(8012);
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    bool ok = connect(descriptor, reinterpret_cast<sockaddr *>(&address), sizeof(address)) == 0 &&
        InvokeTouch(descriptor, "Driver.create", "[]", true) &&
        session.FrameSize(&currentWidth, &currentHeight) && currentWidth == width && currentHeight == height &&
        InvokeTouch(descriptor, api, args, false);
    close(descriptor);
    return ok;
}

static bool HostFrameSize(int *width, int *height) { return session.FrameSize(width, height); }
static bool HostReadBgr(uint8_t *output, size_t capacity, int width, int height)
{
    return session.ReadBgr(output, capacity, width, height);
}
static bool HostClick(int x, int y, int width, int height)
{
    if (x < 0 || y < 0 || x >= width || y >= height) return false;
    return NativeTouch("Driver.longClick", "[" + std::to_string(x) + "," + std::to_string(y) + "]", width, height);
}
static bool HostSwipe(int x1, int y1, int x2, int y2, int duration, int width, int height)
{
    if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0 || x1 >= width || x2 >= width || y1 >= height || y2 >= height) return false;
    int speed = duration > 0 ? static_cast<int>(1000 * std::hypot(x2 - x1, y2 - y1) / duration) : 600;
    speed = std::max(200, std::min(15000, speed));
    return NativeTouch("Driver.swipe", "[" + std::to_string(x1) + "," + std::to_string(y1) + "," +
        std::to_string(x2) + "," + std::to_string(y2) + "," + std::to_string(speed) + "]", width, height);
}

static napi_value CoreStatus(napi_env env, napi_callback_info)
{
    static void *library = nullptr;
    if (!library) library = dlopen("libMaaCore.so", RTLD_NOW | RTLD_LOCAL);
    std::string message;
    if (!library) {
        const char *error = dlerror();
        message = "MaaCore unavailable: " + std::string(error ? error : "unknown loader error");
    } else {
        auto setHost = reinterpret_cast<bool (*)(const MaaHarmonyHost *)>(dlsym(library, "AsstSetHarmonyHost"));
        auto version = reinterpret_cast<const char *(*)()>(dlsym(library, "AsstGetVersion"));
        MaaHarmonyHost host{1, HostFrameSize, HostReadBgr, HostClick, HostSwipe};
        message = setHost && version && setHost(&host) ? "MaaCore host registered: " + std::string(version()) : "MaaCore ABI mismatch";
    }
    napi_value result;
    napi_create_string_utf8(env, message.c_str(), message.size(), &result);
    return result;
}

struct ClickWork {
    napi_async_work work = nullptr;
    napi_deferred deferred = nullptr;
    int32_t x = 0, y = 0, width = 0, height = 0;
    bool success = false;
};

static napi_value NativeClick(napi_env env, napi_callback_info info)
{
    size_t count = 4;
    napi_value args[4];
    ClickWork input;
    napi_get_cb_info(env, info, &count, args, nullptr, nullptr);
    if (count != 4 || napi_get_value_int32(env, args[0], &input.x) != napi_ok ||
        napi_get_value_int32(env, args[1], &input.y) != napi_ok ||
        napi_get_value_int32(env, args[2], &input.width) != napi_ok ||
        napi_get_value_int32(env, args[3], &input.height) != napi_ok) {
        napi_throw_type_error(env, nullptr, "Expected x, y, screen width, screen height");
        return nullptr;
    }
    auto *work = new ClickWork(input);
    napi_value promise, label;
    napi_create_string_utf8(env, "HarmonyNativeClick", NAPI_AUTO_LENGTH, &label);
    if (napi_create_promise(env, &work->deferred, &promise) != napi_ok) { delete work; return nullptr; }
    napi_status status = napi_create_async_work(env, nullptr, label,
        [](napi_env, void *data) {
            auto *task = static_cast<ClickWork *>(data);
            task->success = HostClick(task->x, task->y, task->width, task->height);
        },
        [](napi_env context, napi_status status, void *data) {
            auto *task = static_cast<ClickWork *>(data);
            napi_value result;
            napi_get_boolean(context, status == napi_ok && task->success, &result);
            napi_resolve_deferred(context, task->deferred, result);
            napi_delete_async_work(context, task->work);
            delete task;
        }, work, &work->work);
    if (status != napi_ok || napi_queue_async_work(env, work->work) != napi_ok) {
        napi_value result;
        napi_get_boolean(env, false, &result);
        napi_resolve_deferred(env, work->deferred, result);
        if (work->work) napi_delete_async_work(env, work->work);
        delete work;
    }
    return promise;
}

static napi_value Start(napi_env env, napi_callback_info info)
{
    size_t count = 3;
    napi_value args[3];
    int32_t width = 0, height = 0;
    size_t length = 0;
    napi_get_cb_info(env, info, &count, args, nullptr, nullptr);
    if (count != 3 || napi_get_value_int32(env, args[0], &width) != napi_ok ||
        napi_get_value_int32(env, args[1], &height) != napi_ok ||
        napi_get_value_string_utf8(env, args[2], nullptr, 0, &length) != napi_ok || length > 4096) {
        napi_throw_type_error(env, nullptr, "Expected width, height and private files directory");
        return nullptr;
    }
    std::vector<char> path(length + 1);
    napi_get_value_string_utf8(env, args[2], path.data(), path.size(), &length);
    napi_value result;
    napi_create_int32(env, session.Start(width, height, path.data()), &result);
    return result;
}

static napi_value Stop(napi_env env, napi_callback_info)
{
    session.Stop();
    napi_value result;
    napi_get_undefined(env, &result);
    return result;
}

static napi_value Status(napi_env env, napi_callback_info)
{
    auto status = session.Status();
    napi_value result;
    napi_create_string_utf8(env, status.c_str(), status.size(), &result);
    return result;
}

static napi_value MatchSettings(napi_env env, napi_callback_info)
{
    auto status = session.MatchSettings();
    napi_value result;
    napi_create_string_utf8(env, status.c_str(), status.size(), &result);
    return result;
}

static napi_value Init(napi_env env, napi_value exports)
{
    napi_property_descriptor properties[] = {
        {"start", nullptr, Start, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"stop", nullptr, Stop, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"status", nullptr, Status, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"matchSettings", nullptr, MatchSettings, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"coreStatus", nullptr, CoreStatus, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"nativeClick", nullptr, NativeClick, nullptr, nullptr, nullptr, napi_default, nullptr}
    };
    napi_define_properties(env, exports, sizeof(properties) / sizeof(properties[0]), properties);
    napi_add_env_cleanup_hook(env, [](void *) { session.Stop(); }, nullptr);
    return exports;
}

static napi_module module = {1, 0, nullptr, Init, "frameprobe", nullptr, {nullptr}};
extern "C" __attribute__((constructor)) void RegisterFrameProbe() { napi_module_register(&module); }
