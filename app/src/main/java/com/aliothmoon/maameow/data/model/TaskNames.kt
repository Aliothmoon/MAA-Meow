package com.aliothmoon.maameow.data.model

import android.content.Context
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.utils.i18n.UiText
import com.aliothmoon.maameow.utils.i18n.resolve
import com.aliothmoon.maameow.utils.i18n.uiTextDynamic
import com.aliothmoon.maameow.utils.i18n.uiTextJoin
import com.aliothmoon.maameow.utils.i18n.uiTextOf

/**
 * 任务节点名与配置档名的存储约定：默认名不把语言写进存档。
 *
 * 默认名存成键（`@type:WAKE_UP`、`@profile:3`），显示时再按当前语言渲染；只有用户手动改过的
 * 名字才原样存字符串。重复出来的节点/配置档在键后追加 " N"（与旧的「名称 N」规则一致），
 * 所以切语言后编号仍然跟着走。
 */
const val AUTO_TASK_NAME_PREFIX = "@type:"

/** 旧版硬编码的配置档名前缀（`配置-N`），只在迁移旧存档时用到 */
const val LEGACY_PROFILE_NAME_PREFIX = "配置-"

private const val AUTO_PROFILE_NAME_PREFIX = "@profile:"

private val AUTO_TASK_NAME_REGEX = Regex("^@type:([A-Z_]+)( \\d+)?$")
private val AUTO_PROFILE_NAME_REGEX = Regex("^@profile:(\\d+)$")

/** 由任务类型推导出的「跟随语言」的名字键 */
fun autoTaskName(typeInfo: TaskTypeInfo): String = "$AUTO_TASK_NAME_PREFIX${typeInfo.name}"

/** 解析自动节点名 → (任务类型, 重复编号后缀，如 " 2")；自定义名返回 null */
fun parseAutoTaskName(name: String): Pair<TaskTypeInfo, String>? {
    val match = AUTO_TASK_NAME_REGEX.matchEntire(name) ?: return null
    val typeInfo = TaskTypeInfo.entries.firstOrNull { it.name == match.groupValues[1] } ?: return null
    return typeInfo to match.groupValues[2]
}

/**
 * 默认档名：创建时按当时的界面语言落成字面量，之后不再随语言变。
 *
 * 曾经存的是键 `@profile:N`，显示时再按当前语言渲染；这样切语言时旧档名会跟着变，
 * 但存档里留下不透明键、跨语言导入还会被改名。按维护者 Whiffling 的口径改成创建即定名。
 */
fun defaultProfileName(context: Context, number: Int): String =
    context.getString(R.string.profile_default_name, number)

/** 旧存档里的键 `@profile:N`，只在迁移旧存档时用到 */
fun parseAutoProfileName(name: String): Int? =
    AUTO_PROFILE_NAME_REGEX.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()

fun parseLegacyProfileName(name: String): Int? =
    if (name.startsWith(LEGACY_PROFILE_NAME_PREFIX)) {
        name.removePrefix(LEGACY_PROFILE_NAME_PREFIX).toIntOrNull()
    } else {
        null
    }

/**
 * 从默认档名里取编号：兼容旧键 `@profile:N`、旧硬编码 `配置-N` 与当前语言的默认名格式
 *（`配置-3` / `Profile 3`），用于新增档时算下一个编号。
 */
fun parseDefaultProfileName(context: Context, name: String): Int? {
    parseAutoProfileName(name)?.let { return it }
    parseLegacyProfileName(name)?.let { return it }
    val sample = context.getString(R.string.profile_default_name, 0)
    val at = sample.indexOf('0')
    if (at < 0) return null
    val pattern = "^" + Regex.escape(sample.substring(0, at)) + "(\\d+)" +
        Regex.escape(sample.substring(at + 1)) + "$"
    return Regex(pattern).matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
}

fun taskTypeInfoForConfig(config: TaskParamProvider): TaskTypeInfo? =
    TaskTypeInfo.entries.firstOrNull { it.defaultConfig()::class == config::class }

/**
 * 默认名比较用的归一化：去掉空白、忽略大小写。
 *
 * 旧版本的英文默认名曾是「DepotMaintain」（无空格），与当前资源文案「Depot Maintain」不完全
 * 相等；按字面比较会漏迁移，节点名就永远停在创建时那门语言。
 */
fun normalizeTaskName(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()

/** 节点显示名：自动名按当前语言渲染，自定义名原样返回 */
fun TaskChainNode.nameUiText(): UiText = nameUiText(suffix = null)

/**
 * 节点显示名 + 附加后缀（如库存保持的计划序号 `#3`）。
 * 自定义名是字面量，直接拼成字符串；自动名保持按语言渲染，后缀另起一段。
 */
fun TaskChainNode.nameUiText(suffix: String?): UiText {
    val parsed = parseAutoTaskName(name)
    if (parsed == null) {
        val text = if (suffix.isNullOrBlank()) name else "$name $suffix"
        return uiTextDynamic(text)
    }
    val (typeInfo, numberSuffix) = parsed
    val number = numberSuffix.trim()
    val parts = buildList {
        add(uiTextOf(typeInfo.nameRes))
        if (number.isNotEmpty()) add(uiTextDynamic(number))
        if (!suffix.isNullOrBlank()) add(uiTextDynamic(suffix))
    }
    return if (parts.size == 1) {
        parts.single()
    } else {
        uiTextJoin(*parts.toTypedArray(), separator = UiText.Dynamic(" "))
    }
}

fun TaskChainNode.displayName(context: Context): String = nameUiText().resolve(context)

/**
 * 配置档显示名：存档里存的就是最终字符串（创建/改名时定下），原样显示即可。
 *
 * 只有在还没迁移过的旧键 `@profile:N` 上才会走到「按语言渲染」——迁移会在载入时把它换成字面量。
 */
fun profileNameUiText(name: String): UiText =
    parseAutoProfileName(name)?.let { uiTextOf(R.string.profile_default_name, it) } ?: uiTextDynamic(name)

/** 配置档显示名：见 [profileNameUiText] */
fun TaskProfile.displayName(context: Context): String =
    profileNameUiText(name).resolve(context)
