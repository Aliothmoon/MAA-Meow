package com.aliothmoon.maameow.remote

import android.annotation.SuppressLint
import android.os.MemoryFile
import android.os.ParcelFileDescriptor
import com.aliothmoon.maameow.MaaCoreCallback
import com.aliothmoon.maameow.MaaCoreService
import com.aliothmoon.maameow.maa.AsstApiCallback
import com.aliothmoon.maameow.maa.CallbackJsonAbbreviator
import com.aliothmoon.maameow.maa.MaaCoreLibrary
import com.aliothmoon.maameow.third.Ln
import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.io.FileDescriptor
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess


class MaaCoreServiceImpl(private val ctx: MaaCoreLibrary?) : MaaCoreService.Stub() {

    companion object {
        private const val TAG = "MaaCoreService"

        private const val DEFAULT_IMAGE_BUFFER_SIZE = 3840L * 2160 * 4
    }

    private val instance = AtomicReference<Pointer>()
    private val callback = AtomicReference<MaaCoreCallback?>()
    /**
     * MaaCore 回调入口，运行在 core 的 msg_proc 线程上。
     *
     * **先转发再记日志**：日志是诊断用途，挡在 `onCallback` 前面会给每一条回调
     * 平白加上一次全量字符串拼接 + logcat 写入的延迟。回调送达越快越好 ——
     * App 侧收到 `TaskChainStart` 后还要回写任务参数（见 FightDropsRefresher）。
     */
    private val nativeRef = AsstApiCallback { msg, json, _ ->
        val forwarded = runCatching { callback.get()?.onCallback(msg, json) }
        Ln.i("$TAG: Callback: $msg, ${CallbackJsonAbbreviator.abbreviate(json)}")
        forwarded.onFailure {
            // 转发失败是静默的行为改变（丢掉 TaskChainStart → 目标库存不再重算），
            // 必须能在日志包里一眼看出是哪条消息掉了
            Ln.e("$TAG: Callback DROPPED, msg=$msg: ${it.message}")
        }
    }

    override fun destroy() {
        Ln.i("$TAG: destroy()")
        DestroyInstance()
        exitProcess(0)
    }

    override fun SetUserDir(path: String?): Boolean {
        val core = requireMaaCore() ?: return false
        return core.AsstSetUserDir(path).also {
            Ln.i("$TAG: SetUserDir($path) = $it")
        }
    }

    override fun LoadResource(path: String?): Boolean {
        val core = requireMaaCore() ?: return false
        return core.AsstLoadResource(path).also {
            Ln.i("$TAG: LoadResource($path) = $it")
        }
    }

    override fun SetStaticOption(key: Int, value: String?): Boolean {
        val core = requireMaaCore() ?: return false
        return core.AsstSetStaticOption(key, value).also {
            Ln.i("$TAG: SetStaticOption($key, $value) = $it")
        }
    }

    override fun CreateInstance(cb: MaaCoreCallback?): Boolean {
        val core = requireMaaCore() ?: return false

        if (instance.get() != null) {
            Ln.i("$TAG: Destroying existing instance before creating new one")
            DestroyInstance()
        }
        if (cb != null) {
            callback.set(cb)
        }
        instance.set(core.AsstCreateEx(nativeRef, null))

        val success = instance.get() != null
        Ln.i("$TAG: CreateInstance() = $success")
        return success
    }

    override fun DestroyInstance() {
        val handle = instance

        if (handle.get() != null && ctx != null) {
            Ln.i("$TAG: DestroyInstance()")
            ctx.AsstDestroy(handle.get())
        }

        instance.set(null)
    }

    override fun hasInstance(): Boolean {
        return instance.get() != null
    }

    override fun SetInstanceOption(key: Int, value: String?): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstSetInstanceOption(handle, key, value).also {
            Ln.i("$TAG: SetInstanceOption($key, $value) = $it")
        }
    }

    override fun AsyncConnect(
        adbPath: String?,
        address: String?,
        config: String?,
        block: Boolean
    ): Int {
        val core = requireMaaCore() ?: return 0
        val handle = requireHandle() ?: return 0
        return core.AsstAsyncConnect(
            handle,
            adbPath,
            address,
            config,
            if (block) 1.toByte() else 0.toByte()
        ).also {
            Ln.i("$TAG: AsyncConnect($address, block=$block) = $it")
        }
    }

    override fun SetConnectionExtras(name: String?, extras: String?) {
        val core = requireMaaCore() ?: return
        core.AsstSetConnectionExtras(name, extras)
        Ln.i("$TAG: SetConnectionExtras($name)")
    }

    override fun Connected(): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstConnected(handle)
    }

    override fun AppendTask(type: String?, params: String?): Int {
        val core = requireMaaCore() ?: return 0
        val handle = requireHandle() ?: return 0
        return core.AsstAppendTask(handle, type, params).also {
            Ln.i("$TAG: AppendTask($type) = $it")
        }
    }

    override fun SetTaskParams(taskId: Int, params: String?): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstSetTaskParams(handle, taskId, params) != 0.toByte()
    }

    override fun GetTasksList(): IntArray {
        val core = requireMaaCore() ?: return IntArray(0)
        val handle = requireHandle() ?: return IntArray(0)

        val bufferSize = 256L
        val buffer = IntArray(bufferSize.toInt())
        val count = core.AsstGetTasksList(handle, buffer, bufferSize)

        return if (count > 0) {
            buffer.copyOf(count.toInt())
        } else {
            IntArray(0)
        }
    }

    override fun Start(): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstStart(handle).also {
            Ln.i("$TAG: Start() = $it")
        }
    }

    override fun Stop(): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstStop(handle).also {
            Ln.i("$TAG: Stop() = $it")
        }
    }

    override fun Running(): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstRunning(handle)
    }

    override fun BackToHome(): Boolean {
        val core = requireMaaCore() ?: return false
        val handle = requireHandle() ?: return false
        return core.AsstBackToHome(handle).also {
            Ln.i("$TAG: BackToHome() = $it")
        }
    }


    override fun AsyncClick(x: Int, y: Int, block: Boolean): Int {
        val core = requireMaaCore() ?: return 0
        val handle = requireHandle() ?: return 0
        return core.AsstAsyncClick(
            handle,
            x,
            y,
            if (block) 1.toByte() else 0.toByte()
        )
    }

    override fun AsyncScreencap(block: Boolean): Int {
        val core = requireMaaCore() ?: return 0
        val handle = requireHandle() ?: return 0
        return core.AsstAsyncScreencap(
            handle,
            if (block) 1.toByte() else 0.toByte()
        )
    }


    override fun GetImage(): ParcelFileDescriptor? {
        val core = requireMaaCore() ?: return null
        val handle = requireHandle() ?: return null

        return getImageInternal { buffer, size ->
            core.AsstGetImage(handle, buffer, size)
        }
    }

    override fun GetImageBgr(): ParcelFileDescriptor? {
        val core = requireMaaCore() ?: return null
        val handle = requireHandle() ?: return null

        return getImageInternal { buffer, size ->
            core.AsstGetImageBgr(handle, buffer, size)
        }
    }


    private fun getImageInternal(
        getImageFunc: (Pointer, Long) -> Long
    ): ParcelFileDescriptor? {
        return try {
            val buffer = Memory(DEFAULT_IMAGE_BUFFER_SIZE)
            val actualSize = getImageFunc(buffer, DEFAULT_IMAGE_BUFFER_SIZE)

            if (actualSize <= 0) {
                Ln.w("$TAG: GetImage returned size=$actualSize")
                return null
            }

            val memoryFile = MemoryFile("maa_image", actualSize.toInt())
            memoryFile.writeBytes(
                buffer.getByteArray(0, actualSize.toInt()),
                0,
                0,
                actualSize.toInt()
            )

            val fd = getFileDescriptor(memoryFile)
            if (fd != null) {
                ParcelFileDescriptor.dup(fd)
            } else {
                memoryFile.close()
                null
            }
        } catch (e: Exception) {
            Ln.e("$TAG: GetImage error: ${e.message}")
            null
        }
    }


    @SuppressLint("DiscouragedPrivateApi")
    private fun getFileDescriptor(memoryFile: MemoryFile): FileDescriptor? {
        return try {
            val method = MemoryFile::class.java.getDeclaredMethod("getFileDescriptor")
            method.isAccessible = true
            method.invoke(memoryFile) as? FileDescriptor
        } catch (e: Exception) {
            Ln.e("$TAG: getFileDescriptor error: ${e.message}")
            null
        }
    }


    override fun GetVersion(): String {
        val core = requireMaaCore() ?: return "MaaCore not loaded"
        return core.AsstGetVersion() ?: "Unknown"
    }

    override fun GetUUID(): String {
        val core = requireMaaCore() ?: return ""
        val handle = requireHandle() ?: return ""

        val bufferSize = 256L
        val buffer = Memory(bufferSize)
        val size = core.AsstGetUUID(handle, buffer, bufferSize)

        return if (size > 0) {
            buffer.getString(0)
        } else {
            ""
        }
    }


    private fun requireMaaCore(): MaaCoreLibrary? {
        return ctx ?: run {
            Ln.e("$TAG: MaaCore not loaded")
            null
        }
    }

    private fun requireHandle(): Pointer? {
        return instance.get() ?: run {
            Ln.e("$TAG: No instance created")
            null
        }
    }
}
