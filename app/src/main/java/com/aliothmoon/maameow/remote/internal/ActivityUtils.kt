package com.aliothmoon.maameow.remote.internal

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.graphics.Rect
import android.view.Display
import com.aliothmoon.maameow.constant.AndroidVersions
import com.aliothmoon.maameow.remote.internal.ActivityUtils.DISPLAY_NO_TASK
import com.aliothmoon.maameow.third.FakeContext
import com.aliothmoon.maameow.third.Ln
import com.aliothmoon.maameow.third.wrappers.ServiceManager

@SuppressLint("BlockedPrivateApi", "PrivateApi", "DiscouragedPrivateApi")
object ActivityUtils {

    // android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
    private const val WINDOWING_MODE_FULLSCREEN = 1

    // getAppDisplayId：包名无任何运行中任务
    private const val DISPLAY_NO_TASK = -2

    // 启动后等待任务出现在目标屏的窗口
    private const val APP_PIN_TIMEOUT_MS = 10_000L
    private const val APP_PIN_POLL_INTERVAL_MS = 500L

    // 拉回操作后等系统完成 reparent 的时间
    private const val REPIN_SETTLE_MS = 1_000L

    // resizeTask 后等系统把 task bounds 落地、应用重新布局
    private const val RESIZE_SETTLE_MS = 600L

    @Volatile
    var forceFullscreenOnVirtualDisplay: Boolean = false

    /**
     * app 落到虚拟屏后是否把它的 task bounds 拉满整屏。
     * 只对 freeform 宿主（WSA）有意义，见 [fillDisplayIfShrunk]。
     */
    @Volatile
    var fillDisplayOnVirtualDisplay: Boolean = false

    private val setLaunchWindowingMode by lazy {
        runCatching {
            ActivityOptions::class.java
                .getDeclaredMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .also { it.isAccessible = true }
        }.onFailure { Ln.w("setLaunchWindowingMode: reflection failed", it) }.getOrNull()
    }

    /**
     * 以 shell 身份启动指定 Intent 的 Activity，绕过 BAL 限制。
     * [forceFullscreen] 为 true 时无视用户设置强制 FULLSCREEN 窗口模式（用于漂移拉回重试）。
     */
    @JvmStatic
    @JvmOverloads
    fun startActivity(
        intent: Intent,
        displayId: Int = 0,
        forceFullscreen: Boolean = false
    ): Boolean {
        val am = ServiceManager.getActivityManager()
        try {
            val launchOptions = ActivityOptions.makeBasic()
            if (displayId != Display.DEFAULT_DISPLAY) {
                launchOptions.launchDisplayId = displayId
                if (forceFullscreenOnVirtualDisplay || forceFullscreen) {
                    runCatching {
                        setLaunchWindowingMode?.invoke(launchOptions, WINDOWING_MODE_FULLSCREEN)
                    }.onFailure {
                        Ln.e("invoke setLaunchWindowingMode failed", it)
                    }
                }
            }
            val ret = try {
                am.startActivity(intent, launchOptions.toBundle())
            } catch (e: Exception) {
                Ln.w("startActivity failed, returning -1", e)
                -1
            }
            if (ret < 0) {
                Ln.w("startActivity returned error code $ret, fallback to am command")
                return startViaAmCommand(intent, displayId)
            }
            return true
        } catch (e: Exception) {
            Ln.w("startActivity failed, fallback to am command", e)
            return startViaAmCommand(intent, displayId)
        }
    }

    /** API < Q 读不出任务在哪块屏上，只能拿「上次是我们把它拉到这块屏上的」当它占着屏的依据 */
    @Volatile
    private var lastLaunched: Pair<String, Int>? = null

    /**
     * 强杀 [packageName]；它占着 [displayId] 时等画面停稳并把帧缓冲换成黑帧
     *
     * 杀的不是屏上那个应用就不碰帧缓冲：屏上是别人的静止画面，换黑了不会自己回来
     */
    @JvmStatic
    fun forceStop(packageName: String, displayId: Int) {
        val occupiesDisplay = when (val current = getAppDisplayId(packageName)) {
            null -> lastLaunched == (packageName to displayId)
            else -> current == displayId
        }
        ServiceManager.getActivityManager().forceStopPackage(packageName)
        if (occupiesDisplay) StaleFrameGuard.blankAfterKill(displayId, packageName)
    }

    @JvmStatic
    @JvmOverloads
    fun startApp(
        packageName: String,
        displayId: Int,
        forceStop: Boolean = true,
        excludeFromRecents: Boolean = true
    ): Boolean {
        val pm = FakeContext.get().packageManager

        val intent = pm.getLaunchIntentForPackage(packageName) ?: run {
            pm.getLeanbackLaunchIntentForPackage(packageName)
        }

        if (intent == null) {
            Ln.w("Cannot create launch intent for app $packageName")
            return false
        }

        var flag = Intent.FLAG_ACTIVITY_NEW_TASK
        if (excludeFromRecents) {
            flag = flag or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        intent.addFlags(flag)

        if (forceStop) {
            forceStop(packageName, displayId)
        }
        Ln.i("startApp ${intent.component?.flattenToShortString()}")

        return startActivity(intent, displayId).also { started ->
            if (started) lastLaunched = packageName to displayId
        }
    }

    sealed interface DisplayOccupancy {
        data class Occupied(val topPackage: String?) : DisplayOccupancy
        data object Empty : DisplayOccupancy
        data object Unknown : DisplayOccupancy
    }

    /**
     * [displayId] 上有没有任务。API 不支持、任务表为空、有任务读不出 displayId 都算 [DisplayOccupancy.Unknown]：
     * [StaleFrameGuard] 拿 Empty 当「画面已经没人画了」去换黑帧，把判不出当成空了，
     * 一张不再重绘的静止界面就会一直黑下去
     */
    fun probeDisplay(displayId: Int): DisplayOccupancy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return DisplayOccupancy.Unknown
        if (taskDisplayIdField == null) return DisplayOccupancy.Unknown
        return runCatching {
            val am = FakeContext.get().getSystemService(ActivityManager::class.java)
                ?: return DisplayOccupancy.Unknown
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(100)
            val displayIds = tasks.map(::getTaskDisplayId)
            val index = displayIds.indexOf(displayId)
            when {
                index >= 0 -> DisplayOccupancy.Occupied(tasks[index].topActivity?.packageName)
                tasks.isEmpty() || displayIds.any { it < 0 } -> DisplayOccupancy.Unknown
                else -> DisplayOccupancy.Empty
            }
        }.onFailure { Ln.w("probeDisplay: failed", it) }.getOrDefault(DisplayOccupancy.Unknown)
    }

    /**
     * 检查指定包名的应用最近的活动 task 是否运行在给定 displayId 上。
     * API 28 无 TaskInfo.displayId 字段（@hide），宽松返回 true（不拦截）。
     * 任何异常也宽松返回 true，避免误伤。
     */
    fun isAppOnDisplay(packageName: String, targetDisplayId: Int): Boolean {
        return when (getAppDisplayId(packageName)) {
            null -> true // 无法判断，宽松放行
            targetDisplayId -> true
            else -> false
        }
    }

    /**
     * 启动后校验：等待 [packageName] 的任务出现在 [displayId] 上；若发现任务落在其它
     * display（如 One UI / 部分 ROM 会把游戏从虚拟屏挪回主屏，B 服 U8 SDK 二段跳也可能
     * 丢失 launchDisplayId），立即尝试拉回。仅在确认漂移且拉回失败时返回 false。
     */
    @JvmStatic
    @JvmOverloads
    fun ensureAppOnDisplay(
        packageName: String,
        displayId: Int,
        timeoutMs: Long = APP_PIN_TIMEOUT_MS
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            when (val current = getAppDisplayId(packageName)) {
                null -> return true // 无法判断，宽松放行
                displayId -> {
                    // app 已在目标屏上，顺手把 task 拉满整屏：freeform 宿主（WSA）
                    // 会把 maxBounds 钉在首次创建时的尺寸，不重算就永远留黑边
                    fillDisplayIfShrunk(packageName, displayId)
                    return true
                }
                DISPLAY_NO_TASK -> Unit // 任务尚未出现，继续等待
                else -> {
                    Ln.w("ensureAppOnDisplay: $packageName drifted to display $current (expected $displayId), trying to move it back")
                    return repinAppToDisplay(packageName, displayId)
                }
            }
            if (SystemClock.uptimeMillis() >= deadline) {
                Ln.w("ensureAppOnDisplay: no task of $packageName observed within ${timeoutMs}ms, passing leniently")
                return true
            }
            SystemClock.sleep(APP_PIN_POLL_INTERVAL_MS)
        }
    }

    /**
     * app 已在 [displayId] 上时，把它的 task bounds 拉满整屏。
     *
     * 只对虚拟屏生效（displayId == 0 的物理屏不动，那是用户自己的桌面），
     * 并且只在 task 实际尺寸小于整屏时动手，避免每轮都无谓地重建 task。
     */
    @JvmStatic
    fun fillDisplayIfShrunk(packageName: String, displayId: Int): Boolean {
        if (!fillDisplayOnVirtualDisplay) return false
        if (displayId == Display.DEFAULT_DISPLAY) return false
        val taskId = findTaskId(packageName) ?: return false
        val size = ServiceManager.getDisplayManager().getDisplayInfo(displayId).size()
        val w = size.width()
        val h = size.height()
        if (w <= 0 || h <= 0) return false
        val bounds = readTaskBounds(packageName)
        if (bounds != null && bounds.width() >= w && bounds.height() >= h) {
            return false
        }
        Ln.i("fillDisplayIfShrunk: $packageName bounds=${bounds} -> ${w}x$h on display $displayId")
        val ok = resizeTaskToFullDisplay(taskId, w, h)
        if (ok) SystemClock.sleep(RESIZE_SETTLE_MS)
        return ok
    }

    /**
     * 把 [packageName] 的任务拉回 [displayId]：
     * 1. moveRootTaskToDisplay / moveStackToDisplay（hidden API，shell 有 MANAGE_ACTIVITY_TASKS）
     * 2. am display move-stack 命令兜底
     * 3. 重新投放 launch intent（强制 FULLSCREEN），让系统 reparent 现有任务
     */
    @JvmStatic
    fun repinAppToDisplay(packageName: String, displayId: Int): Boolean {
        if (moveAppTaskToDisplay(packageName, displayId)) {
            SystemClock.sleep(REPIN_SETTLE_MS)
            if (isAppOnDisplay(packageName, displayId)) {
                Ln.i("repinAppToDisplay: $packageName moved back to display $displayId")
                fillDisplayIfShrunk(packageName, displayId)
                return true
            }
        }
        val pm = FakeContext.get().packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            if (startActivity(intent, displayId, forceFullscreen = true)) {
                SystemClock.sleep(REPIN_SETTLE_MS)
                if (isAppOnDisplay(packageName, displayId)) {
                    Ln.i("repinAppToDisplay: $packageName relaunched onto display $displayId")
                    fillDisplayIfShrunk(packageName, displayId)
                    return true
                }
            }
        }
        Ln.e("repinAppToDisplay: failed to pin $packageName on display $displayId")
        return false
    }

    /**
     * 返回 [packageName] 最近任务所在的 displayId。
     * null = 无法判断（API < Q / 反射失败 / 异常）；[DISPLAY_NO_TASK] = 无运行中任务。
     * 取最近任务而非任意任务：漂移时新任务落在主屏，虚拟屏上可能残留旧任务。
     */
    private fun getAppDisplayId(packageName: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (taskDisplayIdField == null) return null
        return runCatching {
            val task = findRecentTask(packageName) ?: return@runCatching DISPLAY_NO_TASK
            getTaskDisplayId(task).takeIf { it >= 0 }
        }.getOrNull()
    }

    fun findTaskId(packageName: String): Int? {
        val task = runCatching { findRecentTask(packageName) }.getOrNull() ?: return null
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) task.taskId else task.id
    }

    /**
     * 读 task 当前 bounds（API 29+ 的 RunningTaskInfo.bounds）。读不到返回 null，
     * 调用方按「不确定就照做」处理，宁可多一次 resize 也不要漏。
     */
    private fun readTaskBounds(packageName: String): Rect? = runCatching {
        val task = findRecentTask(packageName) ?: return null
        val field = runCatching {
            ActivityManager.RunningTaskInfo::class.java.getDeclaredField("bounds")
        }.getOrNull() ?: return null
        field.isAccessible = true
        field.get(task) as? Rect
    }.getOrNull()

    private fun findRecentTask(packageName: String): ActivityManager.RunningTaskInfo? {
        val am = FakeContext.get().getSystemService(ActivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return am.getRunningTasks(100).firstOrNull { task ->
            task.topActivity?.packageName == packageName
                    || task.baseActivity?.packageName == packageName
        }
    }

    @Volatile
    private var recentsExcludedTaskId: Int? = null

    /**
     * 把虚拟屏上的 [packageName] 任务强制排除出最近任务；每个任务只试一次，可随巡检反复调用
     *
     * API 37 起 EXCLUDE_FROM_RECENTS 的任务只要可见就进最近任务，不再要求在主屏，
     * 虚拟屏上的游戏因此常驻最近任务；任务级强制排除不受可见性影响
     */
    @JvmStatic
    fun excludeFromRecents(packageName: String, displayId: Int) {
        if (Build.VERSION.SDK_INT < AndroidVersions.API_37_ANDROID_17) return
        val apply = applyForceExcludedFromRecents ?: return
        val task = runCatching { findRecentTask(packageName) }.getOrNull() ?: return
        if (getTaskDisplayId(task) != displayId || task.taskId == recentsExcludedTaskId) return
        recentsExcludedTaskId = task.taskId
        runCatching {
            apply(taskTokenField!!.get(task)!!)
            Ln.i("excludeFromRecents: task ${task.taskId} of $packageName excluded")
        }.onFailure { Ln.w("excludeFromRecents: failed for task ${task.taskId}", it) }
    }

    private val taskTokenField by lazy {
        runCatching { ActivityManager.RunningTaskInfo::class.java.getField("token") }
            .onFailure { Ln.w("taskTokenField: reflection failed", it) }.getOrNull()
    }

    // WindowContainerTransaction#setTaskForceExcludedFromRecents 经 IWindowOrganizerController 提交，需 MANAGE_ACTIVITY_TASKS
    private val applyForceExcludedFromRecents: ((Any) -> Unit)? by lazy {
        runCatching {
            if (taskTokenField == null) return@runCatching null
            val atm = activityTaskManager ?: return@runCatching null
            val organizer = atm.javaClass.getMethod("getWindowOrganizerController").invoke(atm)!!
            val wctClass = Class.forName("android.window.WindowContainerTransaction")
            val setExcluded = wctClass.getMethod(
                "setTaskForceExcludedFromRecents",
                Class.forName("android.window.WindowContainerToken"),
                Boolean::class.javaPrimitiveType,
            )
            val applyTransaction = organizer.javaClass.getMethod("applyTransaction", wctClass)
            val block: (Any) -> Unit = { token ->
                val wct = wctClass.getConstructor().newInstance()
                setExcluded.invoke(wct, token, true)
                applyTransaction.invoke(organizer, wct)
            }
            block
        }.onFailure { Ln.w("applyForceExcludedFromRecents: reflection failed", it) }.getOrNull()
    }

    private fun moveAppTaskToDisplay(packageName: String, displayId: Int): Boolean {
        val taskId = findTaskId(packageName) ?: run {
            Ln.w("moveAppTaskToDisplay: no running task of $packageName")
            return false
        }
        moveTaskToDisplayMethod?.let { method ->
            runCatching {
                method.invoke(activityTaskManager, taskId, displayId)
                Ln.i("moveAppTaskToDisplay: ${method.name}($taskId, $displayId) invoked")
                return true
            }.onFailure { Ln.w("moveAppTaskToDisplay: ${method.name} failed", it) }
        }
        return moveTaskViaAmCommand(taskId, displayId)
    }

    private val activityTaskManager by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .invoke(null, "activity_task")
            Class.forName($$"android.app.IActivityTaskManager$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        }.onFailure { Ln.w("activityTaskManager: reflection failed", it) }.getOrNull()
    }

    // API 31+: moveRootTaskToDisplay；API 29/30: moveStackToDisplay
    private val moveTaskToDisplayMethod by lazy {
        val atm = activityTaskManager ?: return@lazy null
        sequenceOf("moveRootTaskToDisplay", "moveStackToDisplay").firstNotNullOfOrNull { name ->
            runCatching {
                atm.javaClass.getMethod(
                    name,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            }.getOrNull()
        } ?: run {
            Ln.w("moveTaskToDisplayMethod: no candidate method found")
            null
        }
    }

    private fun moveTaskViaAmCommand(taskId: Int, displayId: Int): Boolean {
        return try {
            val args =
                arrayOf("am", "display", "move-stack", taskId.toString(), displayId.toString())
            Ln.i("moveTaskViaAmCommand: exec: ${args.joinToString(" ")}")
            val process = Runtime.getRuntime().exec(args)
            val exitCode = process.waitFor()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            if (stderr.isNotEmpty()) Ln.w("moveTaskViaAmCommand: am stderr: $stderr")
            if (exitCode != 0) {
                Ln.w("moveTaskViaAmCommand: am exited with code $exitCode")
                return false
            }
            true
        } catch (e: Exception) {
            Ln.e("moveTaskViaAmCommand: am command failed", e)
            false
        }
    }

    /**
     * 把 [packageName] 的任务拉满 [displayId] 整屏。
     *
     * WSA 一类宿主把 app 当 freeform 窗口托管，首次创建时就把 task 的 maxBounds 钉死成
     * 窗口当时的内容区（实测 819x461），之后改虚拟屏尺寸、拖 Windows 窗口都不会重算，
     * app 只占屏的一端、另一侧全是黑边，MAA 的模板坐标随之整体错位。
     * resizeTask 会让系统重建 task bounds，把那个常量重算成整屏。
     *
     * 只动 task，不碰 display：wm size 那种覆盖是全局的，会把同一屏上所有应用一起改坏。
     */
    private fun resizeTaskToFullDisplay(
        taskId: Int,
        width: Int,
        height: Int
    ): Boolean {
        val rect = Rect(0, 0, width, height)
        resizeTaskMethod?.let { method ->
            runCatching {
                // 两套签名参数个数不同：3 参带 resizeMode，2 参没有
                val args = if (method.parameterTypes.size >= 3) {
                    arrayOf<Any>(taskId, rect, RESIZE_MODE_USER)
                } else {
                    arrayOf<Any>(taskId, rect)
                }
                method.invoke(activityTaskManager, *args)
                Ln.i("resizeTaskToFullDisplay: ${method.name}($taskId, $rect) invoked")
                return true
            }.onFailure { Ln.w("resizeTaskToFullDisplay: ${method.name} failed", it) }
        }
        return resizeTaskViaAmCommand(taskId, width, height)
    }

    // 与 shell 权限对齐：系统内部用 USER 模式做交互式 resize
    private val RESIZE_MODE_USER = 0

    // API 31+ resizeTask(taskId, Rect, int)；更早的签名是 resizeTask(int, Rect)
    private val resizeTaskMethod by lazy {
        val atm = activityTaskManager ?: return@lazy null
        sequenceOf(
            arrayOf<Class<*>>(
                Int::class.javaPrimitiveType!!,
                Rect::class.java,
                Int::class.javaPrimitiveType!!
            ),
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, Rect::class.java)
        ).firstNotNullOfOrNull { sig ->
            runCatching { atm.javaClass.getMethod("resizeTask", *sig) }.getOrNull()
        } ?: run {
            Ln.w("resizeTaskMethod: no candidate method found")
            null
        }
    }

    private fun resizeTaskViaAmCommand(taskId: Int, width: Int, height: Int): Boolean {
        return try {
            val args = arrayOf(
                "am", "task", "resize",
                taskId.toString(), "0", "0", width.toString(), height.toString()
            )
            Ln.i("resizeTaskViaAmCommand: exec: ${args.joinToString(" ")}")
            val process = Runtime.getRuntime().exec(args)
            val exitCode = process.waitFor()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            if (stderr.isNotEmpty()) Ln.w("resizeTaskViaAmCommand: am stderr: $stderr")
            if (exitCode != 0) {
                Ln.w("resizeTaskViaAmCommand: am exited with code $exitCode")
                return false
            }
            true
        } catch (e: Exception) {
            Ln.e("resizeTaskViaAmCommand: am command failed", e)
            false
        }
    }

    private val taskDisplayIdField by lazy {
        runCatching {
            var cls: Class<*>? = ActivityManager.RunningTaskInfo::class.java
            var field: java.lang.reflect.Field? = null
            while (cls != null && field == null) {
                field = runCatching { cls.getDeclaredField("displayId") }.getOrNull()
                cls = cls.superclass            }
            field?.also { it.isAccessible = true }
        }.onFailure { Ln.w("taskDisplayIdField: reflection failed", it) }.getOrNull()
    }

    private fun getTaskDisplayId(task: ActivityManager.RunningTaskInfo): Int =
        runCatching { taskDisplayIdField?.getInt(task) ?: -1 }.getOrDefault(-1)

    private fun startViaAmCommand(intent: Intent, displayId: Int): Boolean {
        try {
            val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
            val args = if (displayId == Display.DEFAULT_DISPLAY) {
                arrayOf("am", "start", intentUri)
            } else {
                arrayOf("am", "start", "--display", displayId.toString(), intentUri)
            }
            Ln.i("startViaAmCommand: displayId=$displayId, exec: ${args.joinToString(" ")}")
            val process = Runtime.getRuntime().exec(args)
            val exitCode = process.waitFor()
            // am start 输出量极小（远小于管道缓冲），先 waitFor 再读不会死锁
            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            if (stdout.isNotEmpty()) Ln.i("startViaAmCommand: am stdout: $stdout")
            if (stderr.isNotEmpty()) Ln.w("startViaAmCommand: am stderr: $stderr")
            if (exitCode != 0) {
                Ln.w("startViaAmCommand: am exited with code $exitCode")
                return false
            }
            Ln.i("startViaAmCommand: success (exitCode=0)")
            return true
        } catch (e: Exception) {
            Ln.e("startViaAmCommand: am command fallback failed", e)
            return false
        }
    }
}
