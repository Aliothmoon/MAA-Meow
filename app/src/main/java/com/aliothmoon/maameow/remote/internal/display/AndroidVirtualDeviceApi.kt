package com.aliothmoon.maameow.remote.internal.display

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.os.Build
import android.os.IBinder
import android.view.Surface
import androidx.annotation.RequiresApi
import com.aliothmoon.maameow.third.FakeContext
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executor

/** Android 14+ VirtualDeviceManager 隐藏 API 的唯一反射边界。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object AndroidVirtualDeviceApi {
    private const val LOCK_STATE_ALWAYS_UNLOCKED = 1

    private const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 shl 6
    private const val VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 shl 10
    private const val VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 shl 14
    private const val VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED = 1 shl 16

    private const val VIRTUAL_DEVICE_INTERFACE =
        "android.companion.virtual.IVirtualDeviceManager"

    fun createVirtualDevice(associationId: Int): VirtualDeviceHandle {
        val paramsClass = Class.forName("android.companion.virtual.VirtualDeviceParams")
        val builderClass =
            Class.forName("android.companion.virtual.VirtualDeviceParams\$Builder")
        val builder = builderClass.getConstructor().newInstance()
        builderClass.getMethod("setLockState", Int::class.javaPrimitiveType)
            .invokeUnwrapped(builder, LOCK_STATE_ALWAYS_UNLOCKED)
        val params = builderClass.getMethod("build").invokeUnwrapped(builder)

        val aidlClass = Class.forName(VIRTUAL_DEVICE_INTERFACE)
        val aidl = getBinderInterface(Context.VIRTUAL_DEVICE_SERVICE, aidlClass)
        val managerClass = Class.forName("android.companion.virtual.VirtualDeviceManager")
        val managerConstructor = managerClass
            .getDeclaredConstructor(aidlClass, Context::class.java)
            .apply { isAccessible = true }
        val manager = managerConstructor.newInstance(aidl, FakeContext.get())
        val createDevice = managerClass.getMethod(
            "createVirtualDevice",
            Int::class.javaPrimitiveType,
            paramsClass,
        )
        val device = createDevice.invokeUnwrapped(manager, associationId, params)
            ?: throw IllegalStateException("VDM returned no virtual device")
        return VirtualDeviceHandle(device)
    }

    @SuppressLint("WrongConstant")
    fun createVirtualDisplay(
        device: VirtualDeviceHandle,
        name: String,
        width: Int,
        height: Int,
        dpi: Int,
        surface: Surface,
    ): VirtualDisplay {
        val config = VirtualDisplayConfig.Builder(name, width, height, dpi)
            // 保持拆分前的初始化顺序；显示服务可能在无 Surface 时延后注册 display group。
            .setSurface(surface)
            .setFlags(
                VIRTUAL_DISPLAY_FLAG_TRUSTED or
                    VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH or
                    VIRTUAL_DISPLAY_FLAG_OWN_FOCUS or
                    VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
            )
            .build()
        // 从服务返回对象的运行时类查找，兼容不同系统版本的实现类层级。
        val createDisplay = device.delegate.javaClass.getMethod(
            "createVirtualDisplay",
            VirtualDisplayConfig::class.java,
            Executor::class.java,
            VirtualDisplay.Callback::class.java,
        )
        return createDisplay.invokeUnwrapped(
            device.delegate,
            config,
            null,
            null,
        ) as? VirtualDisplay ?: throw IllegalStateException("VDM returned no display")
    }

    private fun getBinderInterface(serviceName: String, aidlClass: Class<*>): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invokeUnwrapped(null, serviceName) as? IBinder
            ?: throw IllegalStateException("$serviceName service is unavailable")
        val stubClass = Class.forName("${aidlClass.name}\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java)
            .invokeUnwrapped(null, binder)
            ?: throw IllegalStateException("Could not bind $serviceName service")
    }
}

internal class VirtualDeviceHandle internal constructor(internal val delegate: Any) : AutoCloseable {
    override fun close() {
        delegate.javaClass.getMethod("close").invokeUnwrapped(delegate)
    }
}

private fun java.lang.reflect.Method.invokeUnwrapped(
    receiver: Any?,
    vararg arguments: Any?,
): Any? = try {
    invoke(receiver, *arguments)
} catch (failure: InvocationTargetException) {
    throw failure.targetException
}
