package com.aliothmoon.maameow.data.notification.live

import android.app.Notification
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.notification.LiveBackend
import com.aliothmoon.maameow.domain.notification.LiveCapability
import com.aliothmoon.maameow.domain.notification.LiveCategory
import com.aliothmoon.maameow.domain.notification.LiveNotifyIds
import com.aliothmoon.maameow.domain.notification.LiveSession
import com.aliothmoon.maameow.domain.notification.LiveUpdatePublisher
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.island.model.TextInfo
import timber.log.Timber
import java.util.concurrent.Executors

class HyperOsFocusPublisher(
    context: Context,
    private val factory: LiveNotificationFactory,
    private val sequenceStore: FocusSequenceStore,
    private val xmsfGate: XmsfNetworkGate,
    private val appSettings: AppSettingsManager,
    promotedDetector: AospPromotedDetector,
) : LiveUpdatePublisher {

    private val appContext = context.applicationContext
    private val plain = PlainNotificationPublisher(appContext, factory, promotedDetector)
    // xmsf 门闸含跨进程 shell 往返；持续更新与结果首浮入队后台单线程，避免阻塞调用方（主线程 collector）
    private val publishExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hyper-focus-pub").apply { isDaemon = true }
    }
    private val holdLock = Any()
    private var progressHeld = false

    // 缓存复用：进度 1Hz 刷新扛不住每次重新解码位图
    private val appIcon: Icon by lazy { loadAppIcon() }

    override val capability: LiveCapability
        get() = plain.capability.copy(
            backend = LiveBackend.HYPER_OS_FOCUS,
            focusLikely = true,
            focusGranted = true,
        )

    override fun build(session: LiveSession): Notification = assemble(session)

    /** 通知发出瞬间就会触发云端鉴权，闸门必须先拿到手；调用方保证非主线程 */
    override fun prepareProgress() {
        holdProgress()
    }

    override fun publish(session: LiveSession) {
        publishExecutor.execute { publishSync(session) }
    }

    // FGS 首发保持同步：startForeground 需复用同一实例
    override fun publishForeground(session: LiveSession): Notification {
        val notification = assemble(session)
        plain.notifyOrSkip(session.sessionId, notification)
        // 正常路径 prepareProgress 已持有；这里只兜底，且不在主线程上做 shell 往返
        if (needsHold(session)) publishExecutor.execute { holdProgress() }
        return notification
    }

    override fun cancel(sessionId: String) {
        // 同步执行：协调器默认 cancel 立即生效，异步化会和随后的 FGS 通知互相覆盖
        if (sessionId == LiveNotifyIds.PROGRESS_SESSION) releaseProgress()
        plain.cancel(sessionId)
    }

    private fun publishSync(session: LiveSession) {
        if (needsHold(session)) holdProgress()
        val notification = assemble(session)
        val firstResultFloat = session.category == LiveCategory.RESULT &&
            session.firstFloat &&
            !isHeld()
        if (firstResultFloat && bypassEnabled()) {
            xmsfGate.pulse { plain.notifyOrSkip(session.sessionId, notification) }
        } else {
            plain.notifyOrSkip(session.sessionId, notification)
        }
    }

    private fun needsHold(session: LiveSession): Boolean =
        session.category == LiveCategory.PROGRESS && session.ongoing

    private fun isHeld(): Boolean = synchronized(holdLock) { progressHeld }

    private fun holdProgress() {
        if (!bypassEnabled()) return
        // 先置位再 acquire，保证 acquire/release 严格配对
        synchronized(holdLock) {
            if (progressHeld) return
            progressHeld = true
        }
        xmsfGate.acquire()
    }

    private fun releaseProgress() {
        val held = synchronized(holdLock) {
            val previous = progressHeld
            progressHeld = false
            previous
        }
        if (held) xmsfGate.release()
    }

    /** 用户可关：关掉后小米设备上岛会被云端鉴权摘除，退化为普通通知 */
    private fun bypassEnabled(): Boolean = appSettings.liveIslandXmsfBypass.value

    private fun assemble(session: LiveSession): Notification {
        val extras = runCatching { buildFocusExtras(session) }
            .onFailure { Timber.e(it, "build HyperOS focus extras failed") }
            .getOrNull()
        return factory.build(
            session,
            requestPromoted = false,
            extras = extras,
            hyperIsland = true,
        )
    }

    private fun buildFocusExtras(session: LiveSession): Bundle {
        val notifyId = LiveNotifyIds.of(session.sessionId)
        val icon = appIcon
        val percent = session.progressPercent()
        val timeoutSec = session.timeoutSec
            ?: if (session.category == LiveCategory.PROGRESS) 86_400 else 120
        val appLabel = appContext.applicationInfo
            .loadLabel(appContext.packageManager)
            .toString()
            .ifBlank { session.capsuleText }
            .take(16)
        val headline = session.title.take(40)
        val body = session.text.take(80)
        val aod = when {
            session.category == LiveCategory.RESULT -> session.capsuleText.take(8)
            percent != null -> "$percent%"
            else -> "…"
        }
        return FocusNotification.buildV3 {
            val appPic = createPicture(PIC_PROGRESS_APP, icon)
            val capsulePic = createPicture(PIC_PROGRESS_CAPSULE, icon)
            business = if (session.category == LiveCategory.PROGRESS) {
                BUSINESS_PROGRESS
            } else {
                BUSINESS_RESULT
            }
            this.notifyId = notifyId.toString()
            updatable = true
            isShowNotification = true
            reopen = "reopen"
            timeout = (timeoutSec / 60).coerceAtLeast(5)
            sequence = sequenceStore.next(notifyId)
            aodTitle = aod
            ticker = "$headline $aod".take(40)
            tickerPic = capsulePic
            filterWhenNoPermission = false
            showSmallIcon = false
            enableFloat = false
            islandFirstFloat = session.firstFloat
            hideDeco = false
            if (session.firstFloat) {
                outEffectSrc = "glow"
            }

            chatInfo {
                // 不设 picProfile：MIUI 会给它叠一个发送方应用角标，而头像本就是本应用图标
                title = headline
                content = body
            }
            if (percent != null) {
                multiProgressInfo {
                    progress = percent
                    color = PROGRESS_COLOR
                }
            }

            island {
                islandProperty = 1
                islandTimeout = timeoutSec
                dismissIsland = false
                islandOrder = false
                bigIslandArea {
                    val isProgress = session.category == LiveCategory.PROGRESS
                    imageTextInfoLeft {
                        type = 1
                        picInfo {
                            type = 1
                            pic = appPic
                        }
                        textInfo {
                            // 左栏=场景与进度：任务名 + "2/5"；百分比只在进度环与 AOD
                            this.title = (if (isProgress) headline else appLabel).take(16)
                            content = (session.progressLabel
                                ?: session.capsuleText.ifBlank { headline }).take(8)
                            showHighlightColor = true
                        }
                    }
                    textInfo = TextInfo().apply {
                        title = (if (isProgress) stripProgressPrefix(session, body) else headline).take(18)
                        content = body.take(32)
                        showHighlightColor = true
                        narrowFont = true
                    }
                }
                smallIslandArea {
                    if (percent == null) {
                        picInfo {
                            type = 1
                            pic = capsulePic
                        }
                    } else {
                        combinePicInfo {
                            picInfo {
                                type = 1
                                pic = capsulePic
                            }
                            progressInfo {
                                progress = percent
                                colorReach = PROGRESS_COLOR
                                colorUnReach = PROGRESS_UNREACH
                                isCCW = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadAppIcon(): Icon {
        val drawable = appContext.applicationInfo.loadIcon(appContext.packageManager)
        val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else drawable.toBitmap()
        return Icon.createWithBitmap(bitmap)
    }

    /** n/m 已在左栏，右栏标题剥掉 "n/m · " 前缀避免重复 */
    private fun stripProgressPrefix(session: LiveSession, body: String): String {
        val label = session.progressLabel ?: return body
        return body.removePrefix("$label · ")
    }

    private companion object {
        const val PIC_PROGRESS_APP = "miui.focus.pic_progress_app"
        const val PIC_PROGRESS_CAPSULE = "miui.focus.pic_progress_capsule"
        const val BUSINESS_PROGRESS = "download_progress"
        const val BUSINESS_RESULT = "maa_task"
        const val PROGRESS_COLOR = "#3482FF"
        const val PROGRESS_UNREACH = "#33FFFFFF"
    }
}
