package com.aliothmoon.maameow;

import android.content.Intent;
import android.view.Surface;
import com.aliothmoon.maameow.ITouchEventCallback;
import com.aliothmoon.maameow.MaaCoreService;
import com.aliothmoon.maameow.remote.PermissionGrantRequest;
import com.aliothmoon.maameow.remote.PermissionStateInfo;

interface RemoteService {

    oneway void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String version() = 2;

    void test(in Map<String,String> map) = 3;

    void screencap(int width, int height) = 4;

    boolean setForcedDisplaySize(int width, int height) = 6;

    boolean clearForcedDisplaySize() = 7;

    MaaCoreService getMaaCoreService() = 9;

    boolean setup(String userDir,boolean isDebug) = 10;

    PermissionStateInfo grantPermissions(in PermissionGrantRequest request) = 11;

    void setMonitorSurface(in Surface surface) = 12;

    boolean setVirtualDisplayMode(int mode) = 13;

    int startVirtualDisplay() = 14;

    void stopVirtualDisplay() = 15;

    oneway void touchDown(int x, int y) = 17;

    oneway void touchMove(int x, int y) = 18;

    oneway void touchUp(int x, int y) = 19;

    oneway void setDisplayPower(boolean on) = 20;

    boolean setPlayAudioOpAllowed(String packageName, boolean isAllowed) = 21;

    int pid() = 22;

    int isAppAlive(String packageName) = 23;

    oneway void heartbeat(int pid) = 24;

    void setVirtualDisplayResolution(int width, int height, int dpi) = 25;

    oneway void setTouchCallback(ITouchEventCallback callback) = 26;

    boolean startActivity(in Intent intent) = 27;

    boolean isPackageInstalled(String packageName) = 28;

    boolean isAppOnVirtualDisplay(String packageName) = 29;

    oneway void setForceFullscreenOnVirtualDisplay(boolean enabled) = 30;

    // 调试用：抓取当前帧缓冲，编码为 PNG 写入 dirPath 目录（由远端 shell 进程直接落盘，
    // 避免跨进程读取 ashmem 被 SELinux 拒绝）。返回保存的绝对路径，失败返回 null。仅调试模式 UI 调用。
    String captureFramePng(String dirPath) = 31;
}
