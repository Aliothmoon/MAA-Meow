#ifdef __OHOS__

#include "HarmonyController.h"

#include <mutex>

#include "MaaUtils/NoWarningCV.hpp"
#include "Utils/Logger.hpp"
#include <meojson/json.hpp>

namespace asst
{
std::mutex harmony_host_mutex;
MaaHarmonyHost harmony_host {};

bool HarmonyController::connect(const std::string&, const std::string&, const std::string& config)
{
    std::lock_guard lock(harmony_host_mutex);
    m_host = harmony_host;
    m_stretch = false;
    if (!config.empty()) {
        auto parsed = json::parse(config);
        if (!parsed || !parsed->is_object()) {
            return false;
        }
        m_stretch = parsed->get("experimental_stretch_16_9", false);
    }
    if (!m_host.frame_size || !m_host.frame_size(&m_width, &m_height) || m_width <= 0 || m_height <= 0 ||
        m_width > 4096 || m_height > 4096) {
        LogError << "Harmony capture is not ready";
        return false;
    }
    return true;
}

bool HarmonyController::inited() const noexcept
{
    int width = 0, height = 0;
    return m_host.frame_size && m_host.frame_size(&width, &height) && width == m_width && height == m_height;
}

bool HarmonyController::screencap(cv::Mat& image, bool)
{
    if (!inited()) {
        return false;
    }
    image.create(m_height, m_width, CV_8UC3);
    if (!m_host.read_bgr(image.data, image.total() * image.elemSize(), m_width, m_height)) {
        image.release();
        return false;
    }
    if (m_stretch) {
        cv::resize(image, image, cv::Size(1280, 720), 0, 0, cv::INTER_AREA);
    }
    return true;
}

bool HarmonyController::click(const Point& point)
{
    const auto [width, height] = get_screen_res();
    return inited() && point.x >= 0 && point.x < width && point.y >= 0 && point.y < height &&
           m_host.click(point.x * m_width / width, point.y * m_height / height, m_width, m_height);
}

bool HarmonyController::swipe(
    const Point& from,
    const Point& to,
    int duration,
    SwipeExtraDirection extra,
    double,
    double,
    bool with_pause)
{
    const auto [width, height] = get_screen_res();
    if (!inited() || with_pause || extra != SwipeExtraDirection::None || from.x < 0 || from.x >= width || from.y < 0 ||
        from.y >= height || to.x < 0 || to.x >= width || to.y < 0 || to.y >= height) {
        LogWarn << "Harmony swipe requires a bounded simple gesture";
        return false;
    }
    return m_host.swipe(
        from.x * m_width / width,
        from.y * m_height / height,
        to.x * m_width / width,
        to.y * m_height / height,
        duration,
        m_width,
        m_height);
}
} // namespace asst

extern "C" bool AsstSetHarmonyHost(const MaaHarmonyHost* host)
{
    if (!host || host->abi_version != 1 || !host->frame_size || !host->read_bgr || !host->click || !host->swipe) {
        return false;
    }
    std::lock_guard lock(asst::harmony_host_mutex);
    asst::harmony_host = *host;
    return true;
}

#endif
