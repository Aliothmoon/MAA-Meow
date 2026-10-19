package com.aliothmoon.maameow;

import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import com.aliothmoon.maameow.ITouchEventCallback;
import com.aliothmoon.maameow.MaaCoreService;
import com.aliothmoon.maameow.remote.PermissionGrantRequest;
import com.aliothmoon.maameow.remote.PermissionStateInfo;
import com.aliothmoon.maameow.remote.ProcessExitRecord;

interface RemoteService {

    oneway void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String version() = 2;

    void test(in Map<String,String> map) = 3;

    void screencap(int width, int height) = 4;

    boolean setForcedDisplaySize(int width, int height) = 6;

    boolean clearForcedDisplaySize() = 7;

    MaaCoreService getMaaCoreService() = 9;

    int setup(String userDir,boolean isDebug) = 10;

    PermissionStateInfo grantPermissions(in PermissionGrantRequest request) = 11;

    // 主线程不等远端，同一 binder 上的 oneway 按序送达
    oneway void setMonitorSurface(in Surface surface) = 12;

    boolean setVirtualDisplayMode(int mode) = 13;

    int startVirtualDisplay() = 14;

    void stopVirtualDisplay() = 15;

    // contact: 手指 id 0..15，与 MotionEvent pointer id 一致
    oneway void touchDown(int x, int y, int contact) = 17;

    oneway void touchMove(int x, int y, int contact) = 18;

    oneway void touchUp(int x, int y, int contact) = 19;

    // 整体取消当前手势，预览退出时仍按着的手指由远端释放
    oneway void touchCancel() = 42;

    // 后台模式游戏实时帧率，未监控返回 -1
    float getGameFps() = 43;

    // 同步返回远端实际结果，oneway 时失败了 App 还当已熄屏
    boolean setDisplayPower(boolean on) = 20;

    boolean setPlayAudioOpAllowed(String packageName, boolean isAllowed) = 21;

    int pid() = 22;

    int isAppAlive(String packageName) = 23;

    void setVirtualDisplayResolution(int width, int height, int dpi) = 25;

    oneway void setTouchCallback(ITouchEventCallback callback) = 26;

    boolean startActivity(in Intent intent) = 27;

    boolean isPackageInstalled(String packageName) = 28;

    boolean isAppOnVirtualDisplay(String packageName) = 29;

    oneway void setForceFullscreenOnVirtualDisplay(boolean enabled) = 30;

    // 调试用：抓取当前帧缓冲，编码为 PNG 写入 dirPath 目录（由远端 shell 进程直接落盘，
    // 避免跨进程读取 ashmem 被 SELinux 拒绝）。返回保存的绝对路径，失败返回 null。仅调试模式 UI 调用。
    String captureFramePng(String dirPath) = 31;

    // 把漂移到其它 display 的应用任务拉回虚拟显示器，成功返回 true
    boolean moveAppToVirtualDisplay(String packageName) = 32;

    int unlock(String credential) = 33;

    int lockAndSleep() = 34;

    int testUnlock(String credential) = 35;

    oneway void startGestureRecord(int timeoutMs) = 36;

    String pollGestureRecord() = 37;

    oneway void cancelGestureRecord() = 38;

    int unlockWithGesture(String gestureJson) = 39;

    int testUnlockGesture(String gestureJson) = 40;

    // HyperOS 发岛时短断 com.xiaomi.xmsf 网络
    boolean setPackageNetworkingEnabled(String packageName, boolean enabled) = 41;

    // ---- MaaCore 独立数据目录（/data/local/tmp，见 CoreDataDir）----
    // 内置资源与 stamp 不符时从 apkPath 重解
    boolean ensureCoreResources(String apkPath, String stamp) = 44;

    boolean applyCoreHotUpdate(in ParcelFileDescriptor zip) = 45;

    String getCoreResourceVersion() = 46;

    boolean putCoreFile(String relPath, in ParcelFileDescriptor src) = 47;

    // 独立目录 debug/ 下的文件相对路径，导出日志时拉取
    List<String> listCoreDebugFiles() = 48;

    ParcelFileDescriptor openCoreDebugFile(String relPath) = 49;

    boolean clearCoreData() = 50;

    // 当前帧编码为 JPEG 直接回传，无帧或失败返回 null；外部通知附图用
    byte[] captureFrameJpeg(int quality) = 51;

    boolean isSmartResolutionEnabled() = 52;

    // 替被杀的上一个提权进程翻系统日志
    String dumpSystemLog(long sinceMs, long untilMs, int pid, String processPrefix) = 53;

    // displayId 上顶层任务的包名，判不出返回 null
    String getTopPackage(int displayId) = 54;

    // 最近一次唤醒解锁（含自测）的步骤与耗时，不含 PIN 与轨迹
    // sinceElapsedMs 为 App 发起解锁时的 elapsedRealtime，更早开始的返回空串
    String getLastWakeTrace(long sinceElapsedMs) = 55;

    // 系统记下的进程退出（含被杀、崩溃），只返回不早于 sinceMs 的；查别的包靠 shell 自带的 DUMP 权限
    List<ProcessExitRecord> getProcessExitRecords(String packageName, long sinceMs, int maxNum) = 56;
}
