package com.aliothmoon.maameow;

import android.view.Surface;
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

    int getVirtualDisplayId() = 16;
}