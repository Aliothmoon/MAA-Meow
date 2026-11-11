package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.LogKind
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.model.RecruitCombination
import com.aliothmoon.maameow.data.model.RecruitOper
import com.aliothmoon.maameow.data.model.RecruitTagsKind
import com.aliothmoon.maameow.data.model.RecruitTagsLog
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.theme.MaaMeowTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// 设备上查看：am start -n com.aliothmoon.maameow/androidx.compose.ui.tooling.PreviewActivity
//   --es composable com.aliothmoon.maameow.presentation.view.panel.LogPanelPreviewKt.LogPanelDemo

private val BASE: LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(1793030471229L), ZoneId.systemDefault())

private fun e(
    offsetMs: Long,
    content: String,
    level: LogLevel,
    kind: LogKind = LogKind.NORMAL,
    task: String? = null,
    recruit: List<RecruitCombination>? = null,
) = LogItem(
    time = BASE.plusNanos(offsetMs * 1_000_000),
    content = content,
    level = level,
    kind = kind,
    task = task,
    recruitTooltip = recruit,
)

// 公招组合不落盘，按真实干员星级手工构造
private val dailyRecruit = listOf(
    RecruitCombination(
        3, listOf("术师干员"),
        listOf(RecruitOper(4, "夜烟"), RecruitOper(4, "远山"), RecruitOper(3, "炎熔"), RecruitOper(3, "史都华德")),
    ),
    RecruitCombination(
        3, listOf("近卫干员"),
        listOf(
            RecruitOper(4, "杜宾"), RecruitOper(4, "艾丝黛尔"),
            RecruitOper(3, "玫兰莎"), RecruitOper(3, "泡普卡"), RecruitOper(3, "月见夜"),
        ),
    ),
    RecruitCombination(
        3, listOf("近战位", "输出"),
        listOf(RecruitOper(4, "杜宾"), RecruitOper(3, "玫兰莎"), RecruitOper(3, "月见夜")),
    ),
)

private fun combo(level: Int, tags: String, opers: String) = RecruitCombination(
    level,
    tags.split(","),
    opers.split(",").map { RecruitOper(it.substringBefore(':').toInt(), it.substringAfter(':')) },
)

private val recruitPool = listOf(
    dailyRecruit,
    listOf(
        combo(3, "狙击干员", "4:白雪,4:杰西卡,3:克洛丝,3:安德切尔,3:空爆"),
        combo(3, "防护", "4:古米,3:米格鲁,3:卡缇"),
        combo(3, "费用回复", "4:清道夫,3:芬,3:香草,3:翎羽"),
    ),
    listOf(
        combo(3, "辅助干员", "4:地灵,4:深海色,3:梓兰"),
        combo(3, "医疗干员", "4:末药,4:调香师,3:芙蓉,3:安赛尔"),
        combo(3, "术师干员", "4:夜烟,4:远山,3:炎熔,3:史都华德"),
    ),
)

private const val DETECTED = "公招识别结果: "
private const val SELECTED = "选择 Tags："

// 补上不落盘的结构化字段：标签块与公招组合
private fun List<LogItem>.enrich(): List<LogItem> {
    var n = 0
    return map { item ->
        when {
            item.content.startsWith(DETECTED) -> item.copy(
                recruitTags = RecruitTagsLog("公招识别结果", item.content.removePrefix(DETECTED).split(" · "), RecruitTagsKind.DETECTED)
            )

            item.content.startsWith(SELECTED) -> item.copy(
                recruitTags = RecruitTagsLog("选择 Tags", item.content.removePrefix(SELECTED).split(" · "), RecruitTagsKind.SELECTED)
            )

            item.content == "3 ★ Tags" -> item.copy(recruitTooltip = recruitPool[n++ % recruitPool.size])
            else -> item
        }
    }
}

// 真实日常 6 任务日志（2026-10-27）
private fun dailyRun(): List<LogItem> = listOf(
    e(7, "开始执行任务，共 6 项", LogLevel.INFO),
    e(24, "设备内存: 可用 4595MB / 共 11593MB (已用 60%)", LogLevel.INFO),
    e(374, "任务开始运行", LogLevel.SUCCESS),
    e(383, "开始任务:开始唤醒", LogLevel.TRACE, LogKind.TASK_START, "开始唤醒"),
    e(91245, "完成任务:开始唤醒\n(用时 0h 1m 30s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "开始唤醒"),
    e(91735, "开始任务:自动公招", LogLevel.TRACE, LogKind.TASK_START, "自动公招"),
    e(114450, "公招识别结果: 近卫干员 · 术师干员 · 近战位 · 新手 · 输出", LogLevel.TRACE),
    e(114465, "3 ★ Tags", LogLevel.INFO, recruit = dailyRecruit),
    e(115169, "已刷新标签", LogLevel.INFO),
    e(118902, "当前槽位已刷新1次", LogLevel.TRACE),
    e(119106, "公招识别结果: 狙击干员 · 近战位 · 新手 · 防护 · 费用回复", LogLevel.TRACE),
    e(119118, "3 ★ Tags", LogLevel.INFO),
    e(119817, "已刷新标签", LogLevel.INFO),
    e(123540, "当前槽位已刷新2次", LogLevel.TRACE),
    e(123730, "公招识别结果: 医疗干员 · 辅助干员 · 术师干员 · 新手 · 费用回复", LogLevel.TRACE),
    e(123738, "3 ★ Tags", LogLevel.INFO),
    e(124441, "已刷新标签", LogLevel.INFO),
    e(128171, "当前槽位已刷新3次", LogLevel.TRACE),
    e(128365, "公招识别结果: 近卫干员 · 狙击干员 · 辅助干员 · 新手 · 费用回复", LogLevel.TRACE),
    e(128369, "3 ★ Tags", LogLevel.INFO),
    e(128572, "选择 Tags：辅助干员", LogLevel.TRACE),
    e(129235, "已确认招募", LogLevel.INFO),
    e(131619, "公招识别结果: 医疗干员 · 辅助干员 · 术师干员 · 近战位 · 新手", LogLevel.TRACE),
    e(131630, "3 ★ Tags", LogLevel.INFO),
    e(131833, "选择 Tags：辅助干员", LogLevel.TRACE),
    e(132415, "已确认招募", LogLevel.INFO),
    e(134849, "公招识别结果: 近卫干员 · 狙击干员 · 近战位 · 治疗 · 群攻", LogLevel.TRACE),
    e(134856, "3 ★ Tags", LogLevel.INFO),
    e(135162, "选择 Tags：狙击干员 · 群攻", LogLevel.TRACE),
    e(135746, "已确认招募", LogLevel.INFO),
    e(138289, "公招识别结果: 狙击干员 · 重装干员 · 医疗干员 · 辅助干员 · 近战位", LogLevel.TRACE),
    e(138297, "3 ★ Tags", LogLevel.INFO),
    e(138503, "选择 Tags：辅助干员", LogLevel.TRACE),
    e(139074, "已确认招募", LogLevel.INFO),
    e(140337, "完成任务:自动公招\n(用时 0h 0m 48s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "自动公招"),
    e(140838, "开始任务:基建换班", LogLevel.TRACE, LogKind.TASK_START, "基建换班"),
    e(173930, "当前设施:发电站 01", LogLevel.TRACE),
    e(217775, "当前设施:发电站 02", LogLevel.TRACE),
    e(238161, "当前设施:发电站 03", LogLevel.TRACE),
    e(256891, "当前设施:办公室 01", LogLevel.TRACE),
    e(284991, "当前设施:控制中枢 01", LogLevel.TRACE),
    e(332392, "当前设施:制造站 01", LogLevel.TRACE),
    e(372276, "当前设施:制造站 02", LogLevel.TRACE),
    e(395823, "当前设施:制造站 03", LogLevel.TRACE),
    e(419220, "当前设施:制造站 04", LogLevel.TRACE),
    e(448928, "当前设施:贸易站 01", LogLevel.TRACE),
    e(538815, "当前设施:贸易站 02", LogLevel.TRACE),
    e(572805, "当前设施:会客室 01", LogLevel.TRACE),
    e(601933, "已赠送线索", LogLevel.TRACE),
    e(650512, "当前设施:回收站 01", LogLevel.TRACE),
    e(667032, "当前设施:宿舍 01", LogLevel.TRACE),
    e(687822, "当前设施:宿舍 02", LogLevel.TRACE),
    e(700668, "当前设施:宿舍 03", LogLevel.TRACE),
    e(713555, "当前设施:宿舍 04", LogLevel.TRACE),
    e(734900, "当前设施:训练室 01", LogLevel.TRACE),
    e(736955, "训练室空闲中", LogLevel.TRACE),
    e(747091, "当前设施:控制中枢 01", LogLevel.TRACE),
    e(809825, "完成任务:基建换班\n(用时 0h 11m 8s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "基建换班"),
    e(810358, "开始任务:理智作战", LogLevel.TRACE, LogKind.TASK_START, "理智作战"),
    e(824219, "已开始行动 1 (6)  理智: 210/210", LogLevel.INFO),
    e(838123, "游戏画面帧率 25 FPS，低于 50，战斗时机可能不准；建议在游戏设置中开启高帧率模式，并关闭省电模式", LogLevel.WARNING),
    e(918089, "1-7 掉落统计:\n赤金 : 1 (+1)\n固源岩 : 1 (+1)\n基础作战记录 : 2 (+2)\n龙门币 : 72 (+72)", LogLevel.TRACE),
    e(925574, "完成任务:理智作战\n(用时 0h 1m 55s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "理智作战"),
    e(926081, "开始任务:信用收支", LogLevel.TRACE, LogKind.TASK_START, "信用收支"),
    e(981551, "完成任务:访问好友", LogLevel.TRACE),
    e(1042920, "完成任务:信用收支\n(用时 0h 1m 56s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "信用收支"),
    e(1043426, "开始任务:领取奖励", LogLevel.TRACE, LogKind.TASK_START, "领取奖励"),
    e(1059290, "完成任务:领取奖励\n(用时 0h 0m 15s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "领取奖励"),
    e(1059326, "任务已全部完成！ (17m 39s)\n理智: 204/210\n理智将在 2026-10-27 00:52 回满。(33m 后)", LogLevel.SUCCESS, LogKind.SUMMARY),
)

private fun failedRun(): List<LogItem> = listOf(
    e(7, "开始执行任务，共 3 项", LogLevel.INFO),
    e(24, "设备内存: 可用 4595MB / 共 11593MB (已用 60%)", LogLevel.INFO),
    e(30, "设备当前处于护眼/夜光模式 (xiaomi:screen_paper_mode_enabled)，可能影响正常图像识别，请手动关闭", LogLevel.WARNING),
    LogItem(
        time = BASE.plusNanos(35_000_000),
        content = "系统拒绝了前台服务权限(FOREGROUND_SERVICE_SPECIAL_USE)，本次无进度通知，切到后台后可能被系统回收",
        level = LogLevel.WARNING,
        tooltip = "通常是系统或应用管控工具限制了本应用的前台服务；可在 root 或 adb 下执行 " +
            "appops set --uid com.aliothmoon.maameow FOREGROUND_SERVICE_SPECIAL_USE allow 后重试",
    ),
    e(374, "任务开始运行", LogLevel.SUCCESS),
    e(383, "开始任务:自动公招", LogLevel.TRACE, LogKind.TASK_START, "自动公招"),
    e(22000, "公招识别结果: 高级资深干员 · 近卫干员 · 输出 · 生存 · 新手", LogLevel.TRACE),
    e(
        22010, "6 ★ Tags", LogLevel.RARE,
        recruit = listOf(
            RecruitCombination(
                6, listOf("高级资深干员", "近卫干员"),
                listOf(RecruitOper(6, "银灰"), RecruitOper(6, "棘刺"), RecruitOper(6, "史尔特尔")),
            ),
            RecruitCombination(
                6, listOf("高级资深干员", "输出"),
                listOf(RecruitOper(6, "能天使"), RecruitOper(6, "艾雅法拉"), RecruitOper(6, "史尔特尔")),
            ),
        ),
    ),
    e(48900, "完成任务:自动公招\n(用时 0h 0m 48s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "自动公招"),
    e(49400, "开始任务:基建换班", LogLevel.TRACE, LogKind.TASK_START, "基建换班"),
    e(82500, "当前设施:发电站 01", LogLevel.TRACE),
    e(126300, "当前设施:发电站 02", LogLevel.TRACE),
    e(171200, "当前设施:制造站 01", LogLevel.TRACE),
    e(214800, "任务出错: 基建换班", LogLevel.ERROR, LogKind.TASK_FAILED, "基建换班"),
    e(215300, "开始任务:理智作战", LogLevel.TRACE, LogKind.TASK_START, "理智作战"),
    e(229200, "已开始行动 1 (6)  理智: 210/210", LogLevel.INFO),
    e(243100, "游戏画面帧率 25 FPS，低于 50，战斗时机可能不准；建议在游戏设置中开启高帧率模式，并关闭省电模式", LogLevel.WARNING),
    e(
        323100,
        "1-7 掉落统计:\n赤金 : 6 (+1)\n固源岩 : 9 (+1)\n基础作战记录 : 12 (+2)\n龙门币 : 432 (+72)\n" +
            "源岩 : 3 (+1)\n装置 : 2 (+1)\n双酮 : 1 (+1)\n糖 : 2 (+1)\n聚酸酯 : 1 (+1)\n异铁碎片 : 1 (+1)",
        LogLevel.TRACE,
    ),
    e(330500, "完成任务:理智作战\n(用时 0h 1m 55s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "理智作战"),
    e(330600, "任务已结束，以下任务出现错误:\n基建换班 (5m 30s)", LogLevel.ERROR, LogKind.SUMMARY),
    e(330610, "理智: 204/210  \n理智将在 2026-10-27 00:43 回满。(36m 后)", LogLevel.MESSAGE, LogKind.SUMMARY),
)

private fun detected(t: Long, vararg tags: String) =
    e(t, DETECTED + tags.joinToString(" · "), LogLevel.TRACE)
        .copy(recruitTags = RecruitTagsLog("公招识别结果", tags.toList(), RecruitTagsKind.DETECTED))

private fun selected(t: Long, vararg tags: String) =
    e(t, SELECTED + tags.joinToString(" · "), LogLevel.TRACE)
        .copy(recruitTags = RecruitTagsLog("选择 Tags", tags.toList(), RecruitTagsKind.SELECTED))

private fun result(t: Long, vararg combos: RecruitCombination): LogItem {
    val level = combos.maxOf { it.starLevel }
    return e(t, "$level ★ Tags", if (level >= 5) LogLevel.RARE else LogLevel.INFO, recruit = combos.toList())
}

private fun tip(t: Long, tag: String, kind: RecruitTagsKind) =
    e(t, "公招提示\n$tag", if (kind == RecruitTagsKind.ROBOT) LogLevel.RECRUIT_ROBOT else LogLevel.RARE)
        .copy(recruitTags = RecruitTagsLog("公招提示", listOf(tag), kind))

// 机械 + 3/4/5/6 星各一次，事件顺序同 MaaCore：识别 → 特殊标签提示 → 结果 → 选择
private fun recruitSamples(): List<LogItem> = listOf(
    e(0, "开始任务:自动公招", LogLevel.TRACE, LogKind.TASK_START, "自动公招"),
    detected(1000, "支援机械", "近战位", "新手", "防护", "治疗"),
    tip(1005, "支援机械", RecruitTagsKind.ROBOT),
    result(1010, combo(1, "支援机械", "1:Castle-3,1:Lancet-2,1:THRM-EX,1:正义骑士号")),
    detected(3000, "近卫干员", "术师干员", "近战位", "新手", "输出"),
    result(3010, *dailyRecruit.toTypedArray()),
    selected(3200, "术师干员"),
    e(3800, "已确认招募", LogLevel.INFO),

    detected(7000, "削弱", "特种干员", "快速复活", "远程位", "治疗"),
    result(
        7010,
        combo(4, "削弱", "5:陨星,5:初雪,4:流星,4:夜烟"),
        combo(4, "快速复活", "5:红,4:砾"),
        combo(4, "特种干员", "5:崖心,5:红,4:暗索,4:砾"),
        combo(3, "治疗", "4:末药,4:调香师,3:芙蓉,3:安赛尔"),
    ),
    selected(7200, "削弱"),
    e(7800, "已确认招募", LogLevel.INFO),

    detected(11000, "资深干员", "近战位", "防护", "先锋干员", "费用回复"),
    tip(11005, "资深干员", RecruitTagsKind.SPECIAL),
    result(
        11010,
        combo(5, "资深干员", "5:德克萨斯,5:凛冬,5:红,5:崖心,5:白面鸮,5:陨星,5:初雪"),
        combo(5, "资深干员,近战位", "5:德克萨斯,5:凛冬,5:红,5:崖心"),
        combo(5, "资深干员,先锋干员", "5:德克萨斯,5:凛冬"),
        combo(3, "费用回复", "4:清道夫,3:芬,3:香草,3:翎羽"),
    ),
    selected(11200, "资深干员", "先锋干员"),
    e(11800, "已确认招募", LogLevel.INFO),

    detected(15000, "高级资深干员", "近卫干员", "输出", "生存", "新手"),
    tip(15005, "高级资深干员", RecruitTagsKind.SPECIAL),
    result(
        15010,
        combo(6, "高级资深干员", "6:银灰,6:陈,6:赫拉格,6:能天使,6:艾雅法拉,6:伊芙利特,6:推进之王,6:闪灵,6:夜莺,6:星熊,6:塞雷娅,6:黑"),
        combo(6, "高级资深干员,近卫干员", "6:银灰,6:陈,6:赫拉格"),
        combo(6, "高级资深干员,输出", "6:能天使,6:艾雅法拉,6:陈,6:黑,6:银灰"),
        combo(3, "近卫干员", "4:杜宾,4:艾丝黛尔,3:玫兰莎,3:泡普卡,3:月见夜"),
    ),
    e(17000, "完成任务:自动公招\n(用时 0h 0m 17s)", LogLevel.SUCCESS, LogKind.TASK_DONE, "自动公招"),
)

private enum class Scenario(val label: String) {
    RECRUIT("公招样例"), REPLAY("回放"), RUNNING("运行中"), DONE("已完成"), FAILED("出错"),
}

@Preview
@Composable
fun LogPanelDemo() {
    var dark by remember { mutableStateOf(false) }
    var scenario by remember { mutableStateOf(Scenario.RECRUIT) }
    var replayTick by remember { mutableIntStateOf(0) }
    val daily = remember { dailyRun().enrich() }
    val failed = remember { failedRun().enrich() }
    val recruit = remember { recruitSamples() }
    var logs by remember { mutableStateOf<List<LogItem>>(emptyList()) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(scenario, replayTick) {
        when (scenario) {
            Scenario.REPLAY -> {
                running = true
                logs = emptyList()
                delay(800)
                for (i in 1..daily.size) {
                    logs = daily.subList(0, i)
                    delay(250)
                }
                running = false
            }

            Scenario.RUNNING -> {
                running = true
                logs = daily.subList(0, daily.indexOfFirst { it.level == LogLevel.WARNING } + 1)
            }

            Scenario.DONE -> {
                running = false
                logs = daily
            }

            Scenario.FAILED -> {
                running = false
                logs = failed
            }

            Scenario.RECRUIT -> {
                running = false
                logs = recruit
            }
        }
    }

    val mode = if (dark) AppSettingsManager.ThemeMode.DARK else AppSettingsManager.ThemeMode.WHITE
    MaaMeowTheme(themeMode = mode) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Scenario.entries.forEach { s ->
                        FilterChip(
                            selected = scenario == s,
                            onClick = {
                                if (s == Scenario.REPLAY && scenario == s) replayTick++
                                scenario = s
                            },
                            label = { Text(s.label) },
                        )
                    }
                    FilterChip(selected = dark, onClick = { dark = !dark }, label = { Text("深色") })
                }
                key(scenario, replayTick) {
                    LogPanel(
                        logs = logs,
                        running = running,
                        onClearLogs = { logs = emptyList() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
