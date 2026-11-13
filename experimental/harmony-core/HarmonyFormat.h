#pragma once

#ifdef __OHOS__
#include <fmt/format.h>
#include <string_view>
#include <utility>

namespace maa_format
{
template <typename... Args>
std::string format(std::string_view pattern, Args&&... args)
{
    return fmt::format(fmt::runtime(pattern), std::forward<Args>(args)...);
}
}
#else
#include <format>
namespace maa_format
{
using std::format;
}
#endif
