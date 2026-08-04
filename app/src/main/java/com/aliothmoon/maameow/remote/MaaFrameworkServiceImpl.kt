package com.aliothmoon.maameow.remote

import com.aliothmoon.maameow.maa.MaaFrameworkLibrary
import com.aliothmoon.maameow.third.Ln
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicReference

class MaaFrameworkServiceImpl(private val lib: MaaFrameworkLibrary?) {

    companion object {
        private const val TAG = "MaaFrameworkService"
        private const val STATUS_SUCCEEDED = 3000
    }

    private val resourceHandle = AtomicReference<Pointer>()
    private val controllerHandle = AtomicReference<Pointer>()
    private val taskerHandle = AtomicReference<Pointer>()

    private fun requireLib(): MaaFrameworkLibrary? {
        if (lib == null) Ln.e("$TAG: MaaFrameworkLibrary not loaded")
        return lib
    }

    fun loadResource(resourcePath: String): Boolean {
        val core = requireLib() ?: return false
        val res = core.MaaResourceCreate() ?: return false
        resourceHandle.set(res)
        val resId = core.MaaResourcePostBundle(res, resourcePath)
        if (resId <= 0) return false
        val status = core.MaaResourceWait(res, resId)
        return status == STATUS_SUCCEEDED
    }

    fun createController(configJson: String = "{}"): Boolean {
        val core = requireLib() ?: return false
        val ctrl = core.MaaAndroidNativeControllerCreate(configJson) ?: return false
        controllerHandle.set(ctrl)
        val connId = core.MaaControllerPostConnection(ctrl)
        val status = core.MaaControllerWait(ctrl, connId)
        return status == STATUS_SUCCEEDED
    }

    fun createTasker(): Boolean {
        val core = requireLib() ?: return false
        val res = resourceHandle.get() ?: return false
        val ctrl = controllerHandle.get() ?: return false
        val tasker = core.MaaTaskerCreate() ?: return false
        val boundRes = core.MaaTaskerBindResource(tasker, res)
        val boundCtrl = core.MaaTaskerBindController(tasker, ctrl)
        if (boundRes.toInt() == 0 || boundCtrl.toInt() == 0) return false
        taskerHandle.set(tasker)
        return core.MaaTaskerInited(tasker).toInt() != 0
    }

    fun runTask(entry: String): Boolean {
        val core = requireLib() ?: return false
        val tasker = taskerHandle.get() ?: return false
        val taskId = core.MaaTaskerPostTask(tasker, entry, null)
        if (taskId <= 0) return false
        val status = core.MaaTaskerWait(tasker, taskId)
        return status == STATUS_SUCCEEDED
    }

    fun stopAll() {
        val core = requireLib() ?: return
        val tasker = taskerHandle.get() ?: return
        core.MaaTaskerPostStop(tasker)
    }

    fun destroy() {
        val core = requireLib()
        taskerHandle.getAndSet(null)?.let { core?.MaaTaskerDestroy(it) }
        controllerHandle.getAndSet(null)?.let { core?.MaaControllerDestroy(it) }
        resourceHandle.getAndSet(null)?.let { core?.MaaResourceDestroy(it) }
    }
}
