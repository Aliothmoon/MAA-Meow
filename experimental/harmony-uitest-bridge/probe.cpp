#include <cstdarg>
#include <cstring>
#include <cstdint>
#include <unistd.h>
#include "extension_c_api.h"
using namespace OHOS::uitest;
static LowLevelFunctions functions{};
extern "C" __attribute__((visibility("default"))) RetCode UiTestExtension_OnInit(UiTestPort port, size_t, char **)
{
    char version[128]{};
    size_t length = 0;
    int versionResult = port.getUiTestVersion({reinterpret_cast<uint8_t *>(version), sizeof(version)-1, &length});
    // The extension ABI communicates table capacity through its first slot.
    functions.callThroughMessage = reinterpret_cast<decltype(functions.callThroughMessage)>(sizeof(functions));
    int initResult = port.initLowLevelFunctions(&functions);
    FILE *report = fopen("/data/local/tmp/maa-uitest-probe.json", "w");
    if (report) {
        fprintf(report, "{\"uid\":%u,\"versionResult\":%d,\"version\":\"%s\",\"initResult\":%d,\"atomicTouch\":%s}\n", getuid(), versionResult, version, initResult, functions.atomicTouch ? "true" : "false");
        fclose(report);
    }
    return initResult;
}
extern "C" __attribute__((visibility("default"))) RetCode UiTestExtension_OnRun() { return 0; }
