package com.aliothmoon.maameow.schedule.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit

/**
 * 国产 ROM 自启动设置页面引导
 *
 * 各厂商有私有的后台自启动管理，无标准 API，只能尝试打开对应的设置 Activity
 *
 * 决策在 [AutoStartResolution]，本类只做 Intent 解析与 prefs 记录
 * 厂商包可见性依赖 manifest 的 <queries> 声明
 */
object AutoStartHelper {

    data class OemEntry(val id: String, val intent: Intent)

    private val AUTOSTART_INTENTS = listOf(
        // Xiaomi MIUI / HyperOS
        OemEntry(
            "xiaomi",
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
        ),
        // OPPO ColorOS
        OemEntry(
            "coloros",
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            ),
        ),
        // OPPO 旧版
        OemEntry(
            "oppo",
            Intent().setComponent(
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            ),
        ),
        // Vivo OriginOS / FuntouchOS
        OemEntry(
            "vivo",
            Intent().setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            ),
        ),
        // Huawei EMUI / HarmonyOS
        OemEntry(
            "huawei",
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            ),
        ),
        // Honor（独立后）
        OemEntry(
            "honor",
            Intent().setComponent(
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            ),
        ),
        // Samsung
        OemEntry(
            "samsung",
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.lool.activity.applist.AppListActivity"
                )
            ),
        ),
        // Meizu Flyme
        OemEntry(
            "meizu",
            Intent().setComponent(
                ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.permission.SmartBGActivity"
                )
            ),
        ),
        // ZTE
        OemEntry(
            "zte",
            Intent().setComponent(
                ComponentName(
                    "com.zte.heartyservice",
                    "com.zte.heartyservice.autostart.HeartifyAutoStartActivity"
                )
            ),
        ),
    )

    private val RESTRICTIVE_MANUFACTURERS = setOf(
        "xiaomi", "redmi", "oppo", "realme", "oneplus",
        "vivo", "iqoo", "huawei", "honor", "samsung",
        "meizu", "smartisan", "letv", "zte", "nubia",
    )

    fun isKnownRestrictiveManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer in RESTRICTIVE_MANUFACTURERS
    }

    /** 命中即停：一台设备只会属于一家厂商，全扫要白做 8 次跨进程 resolveActivity */
    fun resolvableOemIds(context: Context): List<String> = listOfNotNull(
        AUTOSTART_INTENTS
            .firstOrNull { context.packageManager.resolveActivity(it.intent, 0) != null }
            ?.id
    )

    /** null = 无需引导 */
    fun resolveTarget(context: Context): AutoStartTarget? =
        AutoStartResolution.select(resolvableOemIds(context), isKnownRestrictiveManufacturer())

    fun intentFor(context: Context, target: AutoStartTarget): Intent? = when (target) {
        is AutoStartTarget.Oem -> AUTOSTART_INTENTS.firstOrNull { it.id == target.id }?.intent
        AutoStartTarget.AppDetails -> appDetailsIntent(context)
    }

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // ===== 每启动周期最多提醒一次，用户可永久关闭 =====

    private const val PREFS_REMINDED_BOOT_TOKEN = "autostart_reminded_boot_token"
    private const val PREFS_REMINDED_UPTIME = "autostart_reminded_uptime"
    private const val PREFS_NEVER_REMIND = "autostart_never_remind"

    /** 旧版一次性标记，仅用于迁移 */
    private const val PREFS_LEGACY_GUIDED = "autostart_guided"

    /** 读不到（异常 ROM）返回 null，调用方回退 uptime 语义 */
    fun currentBootToken(context: Context): String? = runCatching {
        val count = Settings.Global.getLong(
            context.contentResolver,
            Settings.Global.BOOT_COUNT,
            -1L,
        )
        if (count >= 0) count.toString() else null
    }.getOrNull()

    /** 不迁移的话，老用户升级后会重新挨弹窗 */
    private fun migrateLegacyGuidedFlag(prefs: SharedPreferences) {
        if (!prefs.contains(PREFS_LEGACY_GUIDED)) return
        val guided = prefs.getBoolean(PREFS_LEGACY_GUIDED, false)
        prefs.edit {
            remove(PREFS_LEGACY_GUIDED)
            if (guided) putBoolean(PREFS_NEVER_REMIND, true)
        }
    }

    /** 本启动周期是否应当提醒 */
    fun shouldRemindThisBoot(context: Context, prefs: SharedPreferences): Boolean {
        migrateLegacyGuidedFlag(prefs)
        return AutoStartResolution.shouldRemind(
            neverRemind = prefs.getBoolean(PREFS_NEVER_REMIND, false),
            currentBootToken = currentBootToken(context),
            lastRemindedBootToken = prefs.getString(PREFS_REMINDED_BOOT_TOKEN, null),
            currentUptimeMs = SystemClock.elapsedRealtime(),
            lastRemindedUptimeMs = prefs.getLong(PREFS_REMINDED_UPTIME, -1L).takeIf { it >= 0 },
        )
    }

    fun markRemindedThisBoot(context: Context, prefs: SharedPreferences) {
        val token = currentBootToken(context)
        prefs.edit {
            if (token != null) {
                putString(PREFS_REMINDED_BOOT_TOKEN, token)
            } else {
                putLong(PREFS_REMINDED_UPTIME, SystemClock.elapsedRealtime())
            }
        }
    }

    fun markNeverRemind(prefs: SharedPreferences) {
        prefs.edit { putBoolean(PREFS_NEVER_REMIND, true) }
    }
}
