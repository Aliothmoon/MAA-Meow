#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「界园·电表倒转」的全部资源节点（v24）。

本文件是 tasks.json 里 MeterRewind 相关节点的**唯一真源**：跑一次就整段重建，
不再手工维护 tasks.json。

三个必须说清楚的坑
==================

1) 为什么不能用 onErrorNext 串格子（v18 之前的写法）
----------------------------------------------------
原本是「每格一个节点，未命中就 onErrorNext 跳下一格」。MAA `ProcessTask` 的真实语义是：
候选任务全部识别失败、`cur_retry` 用尽后，跳的是**最后一个成功执行的节点（m_cur_task）
的 onErrorNext**，不是那个失败候选自己的。实机日志（v18）：

    cur_task = @Enter2, to_be_recognized = [@Sel2S1K2]
    ... 重试 20 次，11.4 秒 ...
    to_be_recognized 变成 [@Step]        ← 用的是 @Enter2 的 onErrorNext

于是链子在第一格就断。正确做法是「同一轮内按列表顺序遍历候选，取第一个命中的」——
同一份日志里 @Step 的 14 个候选就是这样逐个试过后命中 @OnMap 的。

2) 为什么「5 次衡如常」不能用 maxTimes 计数（v19 的错）
-----------------------------------------------------
v19 里 `PickHeng.maxTimes = 5` + `exceededNext = [PickLi]` 从来没生效过。
原因是 v19 起**每一轮都是独立的 Custom 任务**（`task_names: ["...@Enter2"]`），
一个任务里只投一次钱，`maxTimes` 的计数在每轮任务开始时天然归零，永远到不了 5。

`maxTimes` 只对「同一个节点连续命中」有效，而投钱一次之后界面必然变化
（选项 → 确认条 → 结果页 → 领奖 → 地图），中间隔着好几个别的节点，计数会断。

资源层没有任何「变量 / 跨任务状态」可用，所以「这次该投衡还是该投厉」只能由
**任务侧决定**：下发不同的入口名。于是这里为每一类轮次各建一条独立的链路：

    H 轮（投衡如常，奖励=收下源石锭）      : ...@HEnter<n> → HCnt... → HTap<i> → HStep
    L 轮（投厉如锋，奖励=要酒一壶 6 烛火）  : ...@LEnter<n> → LCnt... → LTap<i> → LStep
    R 轮（投厉如锋，奖励=要兵器一对 6 藏品）: ...@REnter<n> → RCnt... → RTap<i> → RStep

三条链只共享 `@Done` / `@Abort`（都不产生点击）。R 与 L 的差别**只在奖励那一步**，
其余逐节点复制——资源层没法把「这次要拿什么奖励」带过去。

两种玩法的节奏都由**任务侧**排好再下发（见 Kotlin `MiniGameDelegate`）：

    刷钱：衡 衡 衡 衡 衡 厉 衡 衡 衡 衡 衡 厉 …（5 衡 1 厉）
    换藏品：R R R R R L R R R R R L …（5 次拿藏品 + 1 次拿烛火回血）

换藏品模式第 6 次故意走 L 链拿烛火——因为每次投厉都要消耗烛火，只出不进会把自己耗干。
炸火时多花一次投厉才拿到同一份奖励，藏品实际到手的次数自然少一次，不需要额外的计数逻辑。

3) 为什么模板匹配而不是 OCR
---------------------------
地图上「常乐」标签只有 34x15 px、「剩余烛火」69x16 px，白字带描边压彩色条，
MAA 的 OCR 读不出来（实测整条链全落空）。模板从实机 1280x720 截图裁：
    MeterRewindChangle.png(38x17)     实机两处 0.8994 / 0.8884
    MeterRewindCandle.png(73x20)      实机 0.9578（日志 0.957466，与 opencv 一致）
    MeterRewindGo.png(152x102)        面板展开 1.0000，其余 < 0.50
    MeterRewindRewardCandle.png(72x26) 奖励卡「酒一壶」  自身 1.0000 / 其它卡 <= 0.64
    MeterRewindRewardRelic.png(94x26)  奖励卡「兵器一对」自身 1.0000 / 其它卡 <= 0.59
→ 阈值 0.80（奖励卡 0.78）。另外 MAA 的 algorithm 只认 `MatchTemplate`（写成 `TemplateMatch`
会在加载期把整个节点**静默丢弃**，表现是「任务加入队列失败：Custom」）。

4) 收藏品弹窗的最后一件：NEXT 位置换成「✕」，点的是同一个槽位
--------------------------------------------------------------
前 N-1 件藏品每屏底部都有「▶ NEXT」，**最后一件在同一个位置换成一个白色的 ✕**。
两张实机截图（Screenshot_20261027_150408 = 中间件、174150 = 最后一件）实测：

    NEXT 模板在中间件上 0.9973，位置 (616,538) 72x24  → 槽位中心 (652,550)
    最后一件上 NEXT 只有 0.4228，同一位置是一个 34x34 的 ✕：bbox (635,532)-(668,565)
    两个中心 (652,550) / (651,549) —— 基本是同一个点

**右上角那个 X 别管，那是 MAA-Meow 自己的按钮**（三张截图里位置分毫不差，而且落在
16:9 黑边之外：像素 x 2262~2307，帧内算出来 x≈1356 > 1280，Core 的帧根本不含它）。
用户确认「只有中间偏下、略高于勾的那个 ✕ 能关闭藏品界面」。

所以最后一件的处理方式是：
- 用**底部圆形对勾**做「确实还停在藏品弹窗上」的判定（0.9998 / 0.9973，其它界面 <= 0.452，
  背景干净、不随藏品变化）；
- 判定成立后**点固定槽位 (652,550)**（`ClickRect`），而不是去匹配那个 ✕ ——
  ✕ 压在每件藏品都不一样的风味文字上，做模板不稳，而槽位是固定的。
- 万一 NEXT 模板在中间件上没命中，点到槽位也还是「NEXT」，等于自动纠正，不会丢藏品。
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TASKS = ROOT / "app/src/main/assets/overrides/resource/tasks/tasks.json"

BASE = "JieGarden@Roguelike@MeterRewind"
PREFIX = BASE + "@"

# ---- 模板 ---------------------------------------------------------------
TPL_CHANGLE = "MeterRewindChangle.png"     # 地图上「常乐」标签里的两个字
TPL_CANDLE = "MeterRewindCandle.png"       # 右上角「剩余烛火」
TPL_GO = "MeterRewindGo.png"               # 节点面板「前往出发」
TPL_REWARD_CANDLE = "MeterRewindRewardCandle.png"   # 奖励卡「酒一壶」
TPL_REWARD_RELIC = "MeterRewindRewardRelic.png"     # 奖励卡「兵器一对」
TPL_NEXT = "MeterRewindNext.png"           # 收藏品逐件弹窗底部「▶ NEXT」
TPL_POPUP_DONE = "MeterRewindPopupDone.png"  # 藏品弹窗底部中间的圆形对勾（只做「还在弹窗上」的判定）
TPL_CONFIRM = "JieGarden@Roguelike@CoppersRecastConfirm-NoRetain.png"   # 官方确认按钮
TPL_TICK = "JieGarden@Roguelike@StageEncounterDrawCopperResult.png"     # 官方结果页对勾

TH = 0.80
"""模板阈值。

不用 0.85 的理由：换设备时（尤其 2K/4K 屏降采样到 1280x720）模板分数会比本机低 0.03~0.08，
0.85 只剩 0.05 余量。而误匹配风险很低——实测空格子最高 0.4139、其它界面最高 0.68，
0.80 仍然分得很开。地图守卫（Candle）宁松不紧：误判成「在地图上」只是安静停下，
误判成「不在」才会去点屏幕。"""

# ---- 网格（1280x720 基准）------------------------------------------------
# 行线取实测标签文字行 y=184/288/398/509/616 的相邻中点，上下补边
ROWS = [(140, 96), (236, 107), (343, 110), (453, 109), (562, 98)]
COLS = [(80, 620), (700, 500)]
SLOTS = []  # 1-based，行优先：从上到下、从左到右
for _y, _h in ROWS:
    for _x, _w in COLS:
        SLOTS.append([_x, _y, _w, _h])
GRID = len(SLOTS)  # 10

MAX_INDEX = 4  # 支持「第 1~4 个常乐」

CANDLE_ROI = [1130, 125, 150, 70]
GO_ROI = [1040, 470, 240, 180]
PICK_ROI = [780, 70, 480, 620]
RECT_MOVE = [0, -32, 38, 17]   # 标签条 → 节点图标中心

# 奖励卡三张竖排（要兵器一对 / 要书一卷 / 要酒一壶）：标题条 y 123~170 / 288~335 / 452~500，
# x 835~1250。ROI 盖住整块，因为选项顺序可能变，模板匹配到哪儿就点哪儿。
REWARD_ROI = [840, 110, 400, 400]
REWARD_TH = 0.78
"""奖励卡阈值。实测：自身 1.0000；「酒一壶」模板落在「要书一卷」上 0.6178、
落在「要兵器一对」上 0.6415。0.78 与误匹配之间有 0.14 余量。"""

# 收藏品逐件弹窗：每件藏品占一屏，底部中间偏下是「▶ NEXT」。六件藏品要连点六次，
# 而「立刻额外获得三件收藏品」这类效果会让实际件数变多，所以不能固定点 N 次，
# 只能「看到 NEXT 就点它的位置」，点到 NEXT 消失为止。
NEXT_ROI = [470, 440, 420, 220]     # 只扫屏幕中下这一条，避免误吃别的界面
NEXT_TH = 0.75
"""NEXT 模板阈值。实测：藏品弹窗 1.0000；奖励卡/地图/节点面板/选项/结果页全部 <= 0.476。"""

# 最后一件藏品：底部中间那个「▶ NEXT / ✕」按钮槽位是**固定**的（NEXT 中心 652,550；
# ✕ 的 bbox (635,532)-(668,565) 中心 651,549）。所以判定用背景干净的圆对勾，
# 点击用固定坐标 —— 那个 ✕ 压在每件藏品都不一样的风味文字上，做模板不稳。
POPUP_DONE_ROI = [540, 560, 240, 160]
POPUP_DONE_TH = 0.80
"""圆对勾阈值。实测：两屏藏品弹窗 0.9998 / 0.9973；其余所有界面 <= 0.452。"""

POPUP_SLOT = [652, 550, 1, 1]
"""藏品弹窗底部按钮槽位（1280x720）。中间件这里是「▶ NEXT」，最后一件这里换成「✕」，
两种情况点它都是对的：前者翻到下一件，后者关闭弹窗。"""

OCR_CLEAN = [[r"[\s\u3000\-_—.。,，·「」『』\"'＂“”……]+", ""]]

# 奖励类型 → (模板, OCR 关键词, 说明)
REWARDS = {
    "COIN": (None, ["收下"], "收下（源石锭直接入账）"),
    "CANDLE": (TPL_REWARD_CANDLE, ["要酒一壶", "酒一壶"], "要酒一壶（获得 6 烛火）"),
    "RELIC": (TPL_REWARD_RELIC, ["要兵器一对", "兵器一对"], "要兵器一对（获得 6 件随机收藏品）"),
}

# 变体 → 投钱节点 / 炸火是否补投厉 / 奖励类型
VARIANTS = {
    "H": ("PickHeng", True, "COIN"),      # 投衡如常；奖励=收下源石锭；炸火后补投一次厉如锋
    "L": ("PickLi", False, "CANDLE"),     # 投厉如锋；奖励=要酒一壶（烛火）；本轮已是厉，不再补
    "R": ("PickLi", False, "RELIC"),      # 投厉如锋；奖励=要兵器一对（藏品）；同上
}

LI_EVERY = 6
"""每 6 次一个周期：刷钱模式第 6 次投厉（其余投衡）；换藏品模式第 6 次拿烛火（其余拿藏品）。
必须与 Kotlin 侧 `METER_REWIND_LI_EVERY` 保持一致。"""

SHARED = {"Done", "Abort"}     # 三个变体共用，不带字母前缀


def full(v: str, short: str) -> str:
    if short.startswith("#"):
        return short
    if short in SHARED:
        return f"{PREFIX}{short}"
    return f"{PREFIX}{v}{short}"


# ---- 扫描链（每个变体一套）------------------------------------------------
def build_scan(v: str) -> dict:
    def TAP(i):
        return full(v, f"Tap{i}")

    def CNT(n, layer, i):
        return full(v, f"Cnt{n}S{layer}I{i}")

    def cand(n, layer, after):
        rest = range(after + 1, GRID + 1)
        return [TAP(i) for i in rest] if layer == n else [CNT(n, layer, i) for i in rest]

    nodes = {}
    ready = full(v, "ChangleReady")
    step = full(v, "Step")

    # 点击层：10 个格子共用（H/L 各一份，因为 next 要落到各自的 Step）
    for i in range(1, GRID + 1):
        nodes[f"Tap{i}"] = {
            "Doc": f"第 {i} 格命中「常乐」= 已经数到目标序号，点击它。rectMove 把点击点从"
                   f"标签条上移到节点图标（标签条本身不一定可点）。",
            "algorithm": "MatchTemplate",
            "template": TPL_CHANGLE,
            "roi": SLOTS[i - 1],
            "templThreshold": TH,
            "action": "ClickSelf",
            "rectMove": RECT_MOVE,
            "postDelay": 1500,
            "next": [ready],
            "onErrorNext": [step],
        }

    # 入口：先确认站在残识地图上，再把整层候选交给 Core 顺序遍历
    for n in range(1, MAX_INDEX + 1):
        nodes[f"Enter{n}"] = {
            "Doc": f"第 {n} 个常乐 · 入口（{'投衡' if v == 'H' else '投厉'}轮）。先用模板确认站在"
                   f"残识地图上（右上角『剩余烛火』），再把 {GRID} 个格子按「从上到下、从左到右」"
                   f"放进同一份候选表交给 Core 顺序遍历：前 {n - 1} 个命中只计数不点，"
                   f"第 {n} 个命中才点。不在地图上则转交主路由（只认节点内界面，不会盲点地图）。",
            "algorithm": "MatchTemplate",
            "template": TPL_CANDLE,
            "roi": CANDLE_ROI,
            "templThreshold": TH,
            "action": "DoNothing",
            "next": cand(n, 1, 0) or [full(v, "Done")],
            "onErrorNext": [step],
        }

    # 计数层：命中 = 又数到一个常乐，不点，把候选切到「该格之后」
    for n in range(1, MAX_INDEX + 1):
        for layer in range(1, n):
            for i in range(1, GRID + 1):
                nodes[f"Cnt{n}S{layer}I{i}"] = {
                    "Doc": f"第 {n} 个常乐 · 第 {layer} 层第 {i} 格命中 = 数到第 {layer} 个，不点，"
                           f"跳过它继续数（候选切到第 {i} 格之后）。本格之后没有格子了说明地图上"
                           f"不够 {n} 个，安静结束本轮，不乱点。",
                    "algorithm": "MatchTemplate",
                    "template": TPL_CHANGLE,
                    "roi": SLOTS[i - 1],
                    "templThreshold": TH,
                    "action": "DoNothing",
                    "next": cand(n, layer + 1, i) or [full(v, "Done")],
                    "onErrorNext": [step],
                }
    return nodes


# ---- 玩法节点（每个变体一套）---------------------------------------------
def build_play(v: str, pick: str, with_comp: bool, reward: str) -> dict:
    def F(s):
        return full(v, s)

    def N(*shorts):
        return [F(s) for s in shorts]

    confirm_all = ["ConfirmBtn", "ConfirmBtnWide", "ConfirmText"]
    reward_tpl, reward_texts, reward_desc = REWARDS[reward]
    is_card = reward_tpl is not None
    nodes = {}

    nodes["Step"] = {
        "Doc": "主路由器。顺序即优先级：确认弹窗 → 进门选项(欣然应允/来就来) → 失败后离开 → "
               "结果页对勾 → 领奖(收下/奖励卡) → 收藏品逐件弹窗的 NEXT → 弹窗最后一件的圆对勾 → "
               "本轮投钱(衡/厉) → 常乐面板(前往出发) → 地图守卫 → 最后才是『点空白推进对话』。"
               "ChangleReady 必须排在 OnMap 前面，否则面板打开后会反复点地图上的常乐标签；"
               "NextBtn / PopupDone 必须排在 StageTap 前面，否则会去点屏幕正中而不是 NEXT / 对勾。",
        "algorithm": "JustReturn",
        "next": N(*confirm_all, "AcceptBet", "LeaveOption", "FailDetect", "TickEvent",
                   "TickOCR", "TickRecast", "TakeReward", *(("TakeRewardOCR",) if is_card else ()),
                   "NextBtn", "PopupDone", pick, "ChangleReady", "OnMap", "StageTap"),
        "onErrorNext": N("Wait"),
    }

    nodes["NextBtn"] = {
        "Doc": "收藏品逐件弹窗（非最后一件）。投厉拿到 6 件藏品后，每件占一屏，底部中间是"
               "『▶ NEXT』，必须逐件点过去（点屏幕别处没有用）。件数不固定——有的藏品效果是"
               "「立刻再获得三件」，所以不能固定点 6 次，而是『看到 NEXT 就点 NEXT 的位置』："
               "next 带 #self 连点，直到 NEXT 消失（最多 20 次）。NEXT 一消失说明到了最后一件"
               "（那里换成了 ✕），转 PopupDone 点同一个槽位关闭弹窗。",
        "algorithm": "MatchTemplate",
        "template": TPL_NEXT,
        "roi": NEXT_ROI,
        "templThreshold": NEXT_TH,
        "action": "ClickSelf",
        "postDelay": 900,
        "maxTimes": 20,
        "next": ["#self", F("PopupDone")],
        "exceededNext": N("PopupDone"),
        "onErrorNext": N("PopupDone"),
    }

    nodes["PopupDone"] = {
        "Doc": "收藏品逐件弹窗的**最后一件**。这一屏底部没有『▶ NEXT』，同一个槽位上换成了白色的 ✕"
               "（右上角那个 X 是 MAA-Meow 自己的按钮，不在 Core 的帧里，别管）。"
               "做法：先用底部圆形对勾（背景干净、不随藏品变）确认还停在这一屏，"
               "再点固定槽位 (652,550) —— 中间件那里是 NEXT、最后一件是 ✕，点哪个都对。"
               "连点最多 6 次（万一某件 NEXT 模板没命中也能靠它推进，最多 6 件够用）；"
               "6 次后还停着就转 PopupStuck 安静收尾。",
        "algorithm": "MatchTemplate",
        "template": TPL_POPUP_DONE,
        "roi": POPUP_DONE_ROI,
        "templThreshold": POPUP_DONE_TH,
        "action": "ClickRect",
        "specificRect": POPUP_SLOT,
        "postDelay": 1500,
        "maxTimes": 6,
        "next": ["#self", F("DialogueSkip")],
        "exceededNext": N("PopupStuck"),
        "onErrorNext": N("DialogueSkip"),
    }

    nodes["PopupStuck"] = {
        "Doc": "安全阀：点了 6 次槽位、收藏品弹窗还在。不再继续点，安静结束本轮（下一轮入口"
               "守卫认不出地图就会自己停住，不会盲点）。",
        "algorithm": "JustReturn",
        "action": "DoNothing",
        "next": [],
        "onErrorNext": N("Done"),
    }

    nodes["AcceptBet"] = {
        "Doc": "进门选项。进常乐节点后点空白会弹出：『欣然应允』(消耗50源石锭) 和 『还是算了』；"
               "炸火重来时文案变成『来就来』(同样消耗50源石锭)。只认按钮上的字，不点下面的灰条说明。",
        "algorithm": "OcrDetect",
        "text": ["欣然应允", "来就来"],
        "ocrReplace": OCR_CLEAN,
        "roi": [730, 110, 470, 300],
        "action": "ClickSelf",
        "postDelay": 2200,
        "next": N("AfterPick"),
        "onErrorNext": N("Step"),
    }

    nodes["LeaveOption"] = {
        "Doc": "炸火后进入的『愿赌服输』场景，唯一选项是绿条『离开……』(下面灰字是『倒霉啊』)。"
               "点它之后同样会弹『确定这样做』，所以走 AfterPick。",
        "algorithm": "OcrDetect",
        "text": ["离开"],
        "ocrReplace": OCR_CLEAN,
        "roi": [730, 140, 470, 320],
        "action": "ClickSelf",
        "postDelay": 2200,
        "next": N("AfterPick"),
        "onErrorNext": N("Step"),
    }

    nodes["ConfirmBtn"] = {
        "Doc": "『确定这样做』的花形图标按钮，复用官方模板 + 官方 roi。点完若按钮还在(#self)就"
               "立刻再点，直到消失。",
        "algorithm": "MatchTemplate",
        "template": TPL_CONFIRM,
        "roi": [794, 559, 381, 147],
        "templThreshold": 0.8,
        "action": "ClickSelf",
        "postDelay": 2200,
        "maxTimes": 30,
        "next": ["#self", F("Step")],
        "exceededNext": N("Step"),
        "onErrorNext": N("Step"),
    }

    nodes["ConfirmBtnWide"] = {
        "Doc": "同上，放宽到右下半屏的兜底，只在前一条没命中时用。",
        "algorithm": "MatchTemplate",
        "template": TPL_CONFIRM,
        "roi": [620, 520, 660, 200],
        "templThreshold": 0.74,
        "action": "ClickSelf",
        "postDelay": 2200,
        "maxTimes": 30,
        "next": ["#self", F("Step")],
        "exceededNext": N("Step"),
        "onErrorNext": N("Step"),
    }

    nodes["ConfirmText"] = {
        "Doc": "文字版确认兜底：确认条和『收下』一样是面板药丸条（y≈300~430），所以 roi 盖住整个右半屏。",
        "algorithm": "OcrDetect",
        "text": ["确定这样做", "确定", "确认"],
        "ocrReplace": OCR_CLEAN,
        "roi": [580, 140, 700, 580],
        "action": "ClickSelf",
        "postDelay": 2200,
        "maxTimes": 30,
        "next": ["#self", F("Step")],
        "exceededNext": N("Step"),
        "onErrorNext": N("Step"),
    }

    nodes["AfterPick"] = {
        "Doc": "刚点完选项/离开后的专用等待器：本轮只允许确认弹窗，绝不回主路由重复点同一个选项"
               "（那等于取消选中）。",
        "algorithm": "JustReturn",
        "next": N(*confirm_all, "WaitPick"),
        "onErrorNext": N("WaitPick"),
    }

    nodes["WaitPick"] = {
        "Doc": "等确认按钮出现。最多 10 次(约10秒)，还认不出就安全结束本轮，不盲点。",
        "algorithm": "JustReturn",
        "action": "DoNothing",
        "postDelay": 1000,
        "maxTimes": 10,
        "next": N("AfterPick"),
        "exceededNext": N("Done"),
    }

    nodes["FailDetect"] = {
        "Doc": "炸火判定：结果页底部横条『什么都没有发生』(实测 y≈572)。只认不点。",
        "algorithm": "OcrDetect",
        "text": ["什么都没有发生"],
        "roi": [400, 530, 460, 100],
        "action": "DoNothing",
        "next": N("FailTick"),
        "onErrorNext": N("Step"),
    }

    nodes["TickEvent"] = {
        "Doc": "投钱结果页的对勾。参数对齐官方 StageEncounterDrawCopperResult：roi [562,568,154,152]、"
               "阈值 0.8、next 带 #self 连点到勾消失。实测模板得分 0.996，勾心 (639,646)。",
        "algorithm": "MatchTemplate",
        "template": TPL_TICK,
        "roi": [562, 568, 154, 152],
        "templThreshold": 0.8,
        "action": "ClickSelf",
        "postDelay": 600,
        "maxTimes": 25,
        "next": ["#self", F("Step")],
        "exceededNext": N("TickOCR"),
        "onErrorNext": N("Step"),
    }

    nodes["TickOCR"] = {
        "Doc": "点勾兜底 A：模板没命中时用 OCR 认结果页文字，再定点点勾心 (639,646)。",
        "algorithm": "OcrDetect",
        "text": ["天随人愿", "获得了", "源石锭", "什么都没有发生"],
        "roi": [280, 470, 720, 190],
        "action": "ClickRect",
        "specificRect": [639, 646, 1, 1],
        "postDelay": 900,
        "maxTimes": 15,
        "next": ["#self", F("Step")],
        "exceededNext": N("TickPoint"),
        "onErrorNext": N("Step"),
    }

    nodes["TickPoint"] = {
        "Doc": "点勾兜底 B（只能被动进入）：OCR 也认了 15 次还在结果页，就定点戳勾心 3 次。",
        "algorithm": "JustReturn",
        "action": "ClickRect",
        "specificRect": [639, 646, 1, 1],
        "postDelay": 900,
        "maxTimes": 3,
        "next": N("Step"),
        "exceededNext": N("Step"),
    }

    nodes["TickRecast"] = {
        "Doc": "重新投钱结果页的对勾（右下角），参数对齐官方 CoppersRecastResult。",
        "algorithm": "MatchTemplate",
        "template": TPL_TICK,
        "roi": [1033, 462, 153, 155],
        "templThreshold": 0.8,
        "action": "ClickSelf",
        "postDelay": 600,
        "next": ["#self", F("Step")],
        "onErrorNext": N("Step"),
    }

    nodes["FailTick"] = {
        "Doc": "炸火页的对勾，点掉后进 FailStep（H 轮会优先补投一次厉钱）。",
        "algorithm": "MatchTemplate",
        "template": TPL_TICK,
        "roi": [562, 568, 154, 152],
        "templThreshold": 0.8,
        "action": "ClickSelf",
        "postDelay": 900,
        "maxTimes": 10,
        "next": ["#self", F("FailStep")],
        "exceededNext": N("FailStep"),
        "onErrorNext": N("FailStep"),
    }

    fail_next = N(*confirm_all)
    if with_comp:
        fail_next += N("CompLi")
    fail_next += N("LeaveOption", "AcceptBet", "TakeReward", *(("TakeRewardOCR",) if is_card else ()),
                   "NextBtn", "PopupDone", pick, "ChangleReady", "OnMap", "StageTap")

    nodes["FailStep"] = {
        "Doc": "炸火后的路由器 = Step + 补投。炸火后要先点对勾、再点空白、再点『离开』、再确认，"
               "然后可能重新出现『来就来』的 50 锭选项，所以必须包含和 Step 一样的全部分支。",
        "algorithm": "JustReturn",
        "next": fail_next,
        "onErrorNext": N("Wait"),
    }

    if with_comp:
        nodes["CompLi"] = {
            "Doc": "炸火补偿（只有投衡轮才有）：补投一次『厉如锋』。",
            "algorithm": "OcrDetect",
            "text": ["如锋"],
            "ocrReplace": OCR_CLEAN,
            "roi": PICK_ROI,
            "action": "ClickSelf",
            "postDelay": 2200,
            "maxTimes": 2,
            "next": N("AfterPick"),
            "exceededNext": N("Done"),
            "onErrorNext": N("FailStep"),
        }

    if is_card:
        nodes["TakeReward"] = {
            "Doc": f"奖励选择（模板版）：点「{reward_desc}」。投厉如锋后不是『收下』，而是右侧三张"
                   f"竖排卡片（要兵器一对 / 要书一卷 / 要酒一壶），卡片顺序可能变，所以用文字模板"
                   f"在整块卡片区里找，匹配到哪儿就点哪儿。",
            "algorithm": "MatchTemplate",
            "template": reward_tpl,
            "roi": REWARD_ROI,
            "templThreshold": REWARD_TH,
            "action": "ClickSelf",
            "postDelay": 1800,
            "next": N("AfterReward"),
            "onErrorNext": N("Step"),
        }
        nodes["TakeRewardOCR"] = {
            "Doc": f"奖励选择（OCR 兜底）：模板没命中时才轮到它，认卡片标题文字「{reward_texts[0]}」。",
            "algorithm": "OcrDetect",
            "text": reward_texts,
            "ocrReplace": OCR_CLEAN,
            "roi": REWARD_ROI,
            "action": "ClickSelf",
            "postDelay": 1800,
            "next": N("AfterReward"),
            "onErrorNext": N("Step"),
        }
    else:
        nodes["TakeReward"] = {
            "Doc": "领奖按钮『收下』（绿色药丸条，实测文字中心约 (910,246)）。",
            "algorithm": "OcrDetect",
            "text": ["收下"],
            "ocrReplace": OCR_CLEAN,
            "roi": [740, 160, 520, 220],
            "action": "ClickSelf",
            "postDelay": 1800,
            "next": N("AfterReward"),
            "onErrorNext": N("AfterReward"),
        }

    nodes["AfterReward"] = {
        "Doc": "领奖后：先处理可能出现的确认弹窗，再处理收藏品逐件弹窗（NEXT → 最后一件的圆对勾），"
               "最后才进剧情对话。" if not is_card else
               "点完奖励卡后：卡片点击 → 『确定这样做』确认条 → 收藏品逐件弹窗（每件一屏，底部 NEXT，"
               "最后一件是底部中间的圆对勾）。所以要留出几秒等确认条出现，等不到再考虑 NEXT / 对勾 / "
               "点屏幕推进对话（否则会在奖励界面乱点）。",
        "algorithm": "JustReturn",
        "next": N(*confirm_all, "NextBtn", "PopupDone", *(["RewardWait"] if is_card else []), "DialogueSkip"),
        "onErrorNext": N("NextBtn", "PopupDone", *(["RewardWait"] if is_card else []), "DialogueSkip"),
    }

    if is_card:
        nodes["RewardWait"] = {
            "Doc": "等奖励确认条出现：只等不点，最多 4 次(约5秒)。超时才认为确实没有确认条。",
            "algorithm": "JustReturn",
            "action": "DoNothing",
            "postDelay": 1200,
            "maxTimes": 4,
            "next": N("AfterReward"),
            "exceededNext": N("DialogueSkip"),
        }

    nodes["DialogueSkip"] = {
        "Doc": "领奖后的剧情对话：等一会点一下屏幕中间，通常点 1~2 下就回到节点地图。最多 20 次。",
        "algorithm": "JustReturn",
        "action": "ClickRect",
        "specificRect": [640, 360, 1, 1],
        "postDelay": 1600,
        "maxTimes": 20,
        "next": N("Step"),
        "exceededNext": N("Done"),
    }

    pick_text = "如常" if pick == "PickHeng" else "如锋"
    pick_name = "衡如常" if pick == "PickHeng" else "厉如锋"
    pick_when = {
        "H": "任务侧按轮次选择入口决定（5 衡 1 厉循环里的衡轮）",
        "L": "5 衡 1 厉里的厉轮，以及换藏品模式里第 6 次『回烛火』那一轮",
        "R": "换藏品模式：5 次拿藏品里的任意一轮（第 6 次改走 L 链拿烛火）",
    }[v]
    nodes[pick] = {
        "Doc": f"本轮投『{pick_name}』（{pick_when}）。"
               f"点完只能去 AfterPick 等确认，不能回 Step。",
        "algorithm": "OcrDetect",
        "text": [pick_text],
        "ocrReplace": OCR_CLEAN,
        "roi": PICK_ROI,
        "action": "ClickSelf",
        "postDelay": 2200,
        "maxTimes": 2,
        "next": N("AfterPick"),
        "exceededNext": N("Step"),
        "onErrorNext": N("Step"),
    }

    nodes["ChangleReady"] = {
        "Doc": "『前往出发』按钮出现 = 某个节点已被选中、右侧面板已展开。"
               "模板实测：面板展开 1.0000，其余全部 < 0.50。命中即进入 GoBtn。",
        "algorithm": "MatchTemplate",
        "template": TPL_GO,
        "roi": GO_ROI,
        "templThreshold": TH,
        "action": "DoNothing",
        "next": N("GoBtn"),
        "onErrorNext": N("Step"),
    }

    nodes["GoBtn"] = {
        "Doc": "点『前往出发』（竖排艺术字+READY TO，OCR 认不出，用模板匹配后点按钮中心）。",
        "algorithm": "MatchTemplate",
        "template": TPL_GO,
        "roi": GO_ROI,
        "templThreshold": TH,
        "action": "ClickSelf",
        "postDelay": 2500,
        "next": N("Step"),
        "onErrorNext": N("Step"),
    }

    nodes["OnMap"] = {
        "Doc": "地图守卫(安全阀)：右上角『剩余烛火』模板命中 = 确实站在残识地图上。此时常乐不在"
               "视野内，绝不去点屏幕中心(地图中心是节点区)，安静结束本轮。"
               "模板实测：地图 1.0000，四个节点界面全部 < 0.50。",
        "algorithm": "MatchTemplate",
        "template": TPL_CANDLE,
        "roi": CANDLE_ROI,
        "templThreshold": TH,
        "action": "DoNothing",
        "next": N("Done"),
        "onErrorNext": N("Step"),
    }

    nodes["StageTap"] = {
        "Doc": "兜底『点空白推进』：进节点后的开场白、投钱动画、转场，屏幕上都认不出任何可点元素，"
               "这时点屏幕中间推进。因为排在 OnMap 之后，地图上永远不会走到这里。最多 30 次(约45秒)。",
        "algorithm": "JustReturn",
        "action": "ClickRect",
        "specificRect": [640, 360, 1, 1],
        "postDelay": 1500,
        "maxTimes": 30,
        "next": N("Step"),
        "exceededNext": N("Done"),
    }

    nodes["Wait"] = {
        "Doc": "兜底空等：只等不点。连续 60 次(约90秒)认不出任何界面则安全结束。",
        "algorithm": "JustReturn",
        "action": "DoNothing",
        "postDelay": 1500,
        "maxTimes": 60,
        "next": N("Step"),
        "exceededNext": N("Done"),
    }
    return nodes


def build_shared() -> dict:
    return {
        "Done": {            "Doc": "本轮自然结束。用 DoNothing + 空 next 而不是 Stop：Stop 会杀掉整个任务队列，"
                   "队列里排了 N 轮时表现为『点开始一动不动』。",
            "algorithm": "JustReturn",
            "action": "DoNothing",
            "next": [],
            "onErrorNext": [],
        },
        "Abort": {
            "Doc": "安静结束（保留给外部引用）。同样不用 Stop。",
            "algorithm": "JustReturn",
            "action": "DoNothing",
            "next": [],
        },
    }


def main() -> int:
    data = json.loads(TASKS.read_text(encoding="utf-8"))

    # 1) 整段清掉上次生成的节点（本站所有节点都以 PREFIX 开头；@MeterRewind 本身不含 @）
    for key in [k for k in data if k.startswith(PREFIX)]:
        del data[key]

    # 2) 共用结束节点
    for short, node in build_shared().items():
        data[f"{PREFIX}{short}"] = node

    # 2.5) 通用闸门（列表里登记的 value = BASE，用户手选「通用」任务时才会命中）。
    #      只认界面文字，一个都认不出就安静结束，绝不盲点。
    data[BASE] = {
        "Doc": "界园·电表倒转 通用闸门（仅手选通用任务时使用；正常下发的是带序号的 "
               "@HEnter<n> / @LEnter<n> / @REnter<n>）。识别不到任何已知界面时安静结束本轮，绝不盲点。",
        "algorithm": "OcrDetect",
        "text": ["常乐", "如常", "如锋", "如族", "前往", "出发", "欣然应允", "来就来",
                 "离开", "掷地有声", "收下", "获得", "什么都没有发生", "剩余烛火",
                 "要酒一壶", "要兵器一对", "要书一卷"],
        "ocrReplace": OCR_CLEAN,
        "roi": [0, 40, 1280, 640],
        "action": "DoNothing",
        "next": [f"{PREFIX}HStep"],
        "onErrorNext": [f"{PREFIX}Done"],
    }

    # 3) 每个变体各建一条完整链路
    for v, (pick, with_comp, reward) in VARIANTS.items():
        for short, node in build_play(v, pick, with_comp, reward).items():
            data[full(v, short)] = node
        for short, node in build_scan(v).items():
            data[full(v, short)] = node

    # 缩进/换行与上游一致：2 空格 + LF。否则每次重跑都会把整个文件重写一遍，
    # 让 diff 淹没真正的改动（Windows 下文本模式默认会把 \n 翻成 \r\n，这里显式指定）。
    with TASKS.open("w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")

    counts = {v: sum(1 for k in data if k.startswith(PREFIX + v)) for v in VARIANTS}
    print(f"nodes={len(data)}  grid={GRID}  maxIndex={MAX_INDEX}  " +
          "  ".join(f"{v}={c}" for v, c in counts.items()))

    # 4) 生成完立刻自检：非法 algorithm/action 或缺模板会被 Core 在加载期**静默丢弃**
    # 用进程内 import 调用，不起外部进程（外部进程 + 变量路径会被判为命令注入风险）
    return _run_checker(TASKS)


def _run_checker(tasks_path):
    tools_dir = str(Path(__file__).resolve().parent)
    if tools_dir not in sys.path:
        sys.path.insert(0, tools_dir)
    import validate_overrides_tasks  # noqa: E402  (同目录脚本，运行时才插入 sys.path)
    return validate_overrides_tasks.main(str(tasks_path))


if __name__ == "__main__":
    sys.exit(main())
