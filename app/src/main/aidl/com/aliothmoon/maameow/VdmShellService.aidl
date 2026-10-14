package com.aliothmoon.maameow;

import android.os.IBinder;
import android.view.Surface;

/** 永久保持 shell 身份并持有 Android 14+ VirtualDevice 资源的辅助进程。 */
interface VdmShellService {
    oneway void destroy() = 16777114;

    void attachOwner(IBinder owner) = 1;

    void cleanupStale(int userId) = 2;

    int createDisplay(
        String name,
        int width,
        int height,
        int dpi,
        in Surface surface,
        int userId
    ) = 3;

    void closeDisplay(boolean removeAssociation) = 4;
}
