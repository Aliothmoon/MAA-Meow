#pragma once

#ifdef __OHOS__

#include "ControllerAPI.h"
#include "HarmonyHost.h"

namespace asst
{
class HarmonyController final : public ControllerAPI
{
public:
    bool connect(const std::string&, const std::string&, const std::string&) override;
    bool inited() const noexcept override;

    const std::string& get_uuid() const override { return m_uuid; }

    size_t get_pipe_data_size() const noexcept override { return 0; }

    size_t get_version() const noexcept override { return 1; }

    bool screencap(cv::Mat& image, bool allow_reconnect = false) override;

    bool start_game(const std::string&) override { return false; }

    bool stop_game(const std::string&) override { return false; }

    bool click(const Point& point) override;

    bool input(const std::string&) override { return false; }

    bool swipe(
        const Point& from,
        const Point& to,
        int duration,
        SwipeExtraDirection extra,
        double slope_in,
        double slope_out,
        bool with_pause) override;

    bool inject_input_event(const InputEvent&) override { return false; }

    bool press_esc() override { return false; }

    ControlFeat::Feat support_features() const noexcept override { return ControlFeat::NONE; }

    std::pair<int, int> get_screen_res() const noexcept override
    {
        return m_stretch ? std::pair<int, int> { 1280, 720 } : std::pair<int, int> { m_width, m_height };
    }

private:
    MaaHarmonyHost m_host {};
    int m_width = 0;
    int m_height = 0;
    bool m_stretch = false;
    std::string m_uuid = "harmony-local";
};
} // namespace asst

#endif
