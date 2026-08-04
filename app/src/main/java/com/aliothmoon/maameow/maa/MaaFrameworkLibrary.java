package com.aliothmoon.maameow.maa;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Callback;

/**
 * JNA 接口，对应 MaaFramework 的 C API（替代原 MaaCoreLibrary 里绑定的老 MAA AsstCaller 接口）。
 * 加载的 native 库名应为 "MaaFramework"（对应 libMaaFramework.so），
 * 需要额外把 MaaFramework Android release 里的其它依赖 .so（如 libMaaUtils.so 等，
 * 具体以 release 包内实际文件为准）一并放进 jniLibs。
 */
public interface MaaFrameworkLibrary extends Library {

    // ------------------- Resource -------------------
    Pointer MaaResourceCreate();

    void MaaResourceDestroy(Pointer res);

    long MaaResourcePostBundle(Pointer res, String path);

    int MaaResourceStatus(Pointer res, long resId);

    int MaaResourceWait(Pointer res, long resId);

    byte MaaResourceLoaded(Pointer res);

    // ------------------- Controller -------------------
    Pointer MaaAndroidNativeControllerCreate(String configJson);

    void MaaControllerDestroy(Pointer ctrl);

    long MaaControllerPostConnection(Pointer ctrl);

    int MaaControllerStatus(Pointer ctrl, long ctrlId);

    int MaaControllerWait(Pointer ctrl, long ctrlId);

    byte MaaControllerConnected(Pointer ctrl);

    // ------------------- Tasker -------------------
    Pointer MaaTaskerCreate();

    void MaaTaskerDestroy(Pointer tasker);

    byte MaaTaskerBindResource(Pointer tasker, Pointer res);

    byte MaaTaskerBindController(Pointer tasker, Pointer ctrl);

    byte MaaTaskerInited(Pointer tasker);

    long MaaTaskerPostTask(Pointer tasker, String entry, String pipelineOverride);

    int MaaTaskerStatus(Pointer tasker, long taskId);

    int MaaTaskerWait(Pointer tasker, long taskId);

    byte MaaTaskerRunning(Pointer tasker);

    long MaaTaskerPostStop(Pointer tasker);

    byte MaaTaskerClearCache(Pointer tasker);

    // ------------------- Version -------------------
    String MaaVersion();
}
