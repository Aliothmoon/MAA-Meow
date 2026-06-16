# 成就系统维护指南

本文档说明 MaaMeow 成就系统的实现方式、展示策略、各模块职责，以及如何通过修改 `achievements.json` 新增成就。

## 设计目标

成就系统采用“事件上报 + JSON 规则匹配”的方式实现。业务代码只负责上报“发生了什么”，例如应用启动、任务开始、Copilot 通关；具体解锁哪个成就、累计多少进度、需要什么条件，由 `app/src/main/assets/achievements.json` 配置决定。

这意味着：只要新增成就能复用已有事件，大多数情况下只需要编辑 JSON，不需要再改 Kotlin 代码。

## 核心文件

| 文件 | 作用 |
|------|------|
| `app/src/main/assets/achievements.json` | 成就定义和触发规则配置。新增成就优先改这里 |
| `AchievementModels.kt` | 成就数据模型、触发器模型、事件名常量 |
| `AchievementRepository.kt` | 读取 JSON、保存解锁记录、解释触发规则、发出解锁事件 |
| `AchievementViewModel.kt` | 成就页状态管理、搜索过滤、打开页面事件上报 |
| `AchievementView.kt` | 成就列表 UI、解锁状态展示、占位文本和进度展示 |
| `AppNavigation.kt` | 监听成就解锁事件并显示 Snackbar |
| 各业务模块 | 在对应行为发生时调用 `recordEvent(...)` 上报事件 |

## 展示策略

成就页默认只展示已解锁成就，未解锁成就不会出现在列表中；搜索也只在已解锁成就内过滤。

`hidden=true` 表示成就解锁前不应暴露真实标题、描述和条件。当前成就页不会展示未解锁成就，但 UI 仍保留锁定占位文本，方便调试页、未来入口或其他调用方复用。

统计中的总数来自仓库已加载的成就状态，解锁数来自已解锁记录；列表数量不一定等于总数，因为未解锁成就会被 ViewModel 过滤掉。

## 运行流程

典型流程如下：

```text
业务行为发生
  ↓
业务模块调用 achievementRepository.recordEvent(event, payload, amount)
  ↓
AchievementRepository 查找 achievements.json 中匹配该 event 的 trigger
  ↓
检查 where 和 conditions 是否满足
  ↓
按 mode 更新进度或解锁状态
  ↓
写入 DataStore
  ↓
通过 unlockEvents 通知 UI
  ↓
AppNavigation 显示“达成成就”Snackbar
```

## 成就记录存储

用户的成就进度和解锁状态保存在 Android DataStore 中，DataStore 名称为 `achievements`。

每条记录包含：

| 字段 | 说明 |
|------|------|
| `id` | 成就 ID，对应 JSON 中的 `id` |
| `unlocked` | 是否已解锁 |
| `unlockedAtMillis` | 解锁时间戳 |
| `progress` | 当前进度 |
| `customData` | 连续天数、同日计数等规则需要的辅助数据 |

仓库层使用 `Mutex` 串行化读写，避免多个成就同时触发时互相覆盖存档。

## JSON 基础结构

一个成就定义示例：

```json
{
  "id": "UseCopilot1",
  "title": {
    "zh": "抄作业",
    "en": "Copying Homework"
  },
  "description": {
    "zh": "真正的摆完挂机。",
    "en": "True hands-off gaming."
  },
  "condition": {
    "zh": "使用 ｢{key=Copilot}｣ 功能通关 1 次",
    "en": "Use ｢{key=Copilot}｣ function to clear 1 time"
  },
  "category": "AUTO_BATTLE",
  "group": "UseCopilot",
  "target": 1,
  "hidden": false,
  "rare": false,
  "groupIndex": 1,
  "releasePhase": 1,
  "trigger": {
    "event": "copilot_success",
    "mode": "INCREMENT"
  }
}
```

## 基础字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 成就唯一 ID，不允许重复 |
| `title` | 是 | 多语言标题 |
| `description` | 是 | 多语言描述 |
| `condition` | 是 | 多语言达成条件说明 |
| `category` | 是 | 成就分类，必须是 `AchievementCategory` 中已有枚举 |
| `group` | 否 | 分组名。主要用于排序和表达同系列成就 |
| `target` | 否 | 目标进度。`0` 表示非进度型成就 |
| `hidden` | 否 | 是否隐藏真实信息。解锁前不应暴露标题、描述和条件 |
| `rare` | 否 | 是否稀有，用于 UI 着色 |
| `groupIndex` | 否 | 同组排序序号 |
| `releasePhase` | 否 | 成就发布批次 |
| `trigger` | 否 | 单个触发规则 |
| `triggers` | 否 | 多个触发规则 |

`title`、`description`、`condition` 支持 `{key=...}` 占位符。UI 会按当前语言把已知 key 显示为对应文案，例如中文环境下 `{key=Copilot}` 显示为 `自动战斗`，英文环境下显示为 `Copilot`。未配置映射的 key 会回退显示 key 本身。

## 分类枚举

`category` 必须使用以下值之一：

| 值 | 含义 |
|----|------|
| `BASIC_USAGE` | 基础使用 |
| `FEATURE_EXPLORATION` | 功能探索 |
| `AUTO_BATTLE` | 自动战斗 |
| `HUMOR` | 趣味成就 |
| `BUG_RELATED` | 错误/异常相关 |
| `BEHAVIOR` | 使用行为 |
| `EASTER_EGG` | 彩蛋 |

如果需要新增分类，不能只改 JSON，还需要修改 `AchievementCategory` 枚举和相关 UI 逻辑。

## trigger 字段

`trigger` 描述这个成就如何响应某个事件。

```json
"trigger": {
  "event": "copilot_success",
  "mode": "INCREMENT",
  "amount": 1,
  "where": {
    "task": "StageDrops-Stars-3"
  },
  "conditions": [
    {
      "field": "elapsedMillis",
      "op": "GTE",
      "value": "10800000"
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `event` | 是 | 事件名，必须与 Kotlin 侧上报的事件一致 |
| `mode` | 否 | 触发模式，默认 `UNLOCK` |
| `amount` | 否 | 每次事件增加的基础数量，默认 `1` |
| `where` | 否 | 简单字段相等匹配 |
| `conditions` | 否 | 更灵活的条件匹配 |
| `dateKey` | 否 | 连续天数/同日计数使用的自定义日期键 |

## triggers 字段

如果一个成就可以由多个事件触发，使用 `triggers` 数组。

```json
"triggers": [
  {
    "event": "task_chain_error",
    "mode": "UNLOCK"
  },
  {
    "event": "subtask_error",
    "mode": "UNLOCK"
  }
]
```

`trigger` 和 `triggers` 可以同时存在，但通常只使用其中一个，避免维护混乱。

## 触发模式

| mode | 行为 | 典型用途 |
|------|------|----------|
| `UNLOCK` | 条件满足时直接解锁 | 打开页面、切换语言、触发错误 |
| `INCREMENT` | 增加进度，达到 `target` 后解锁 | 使用 Copilot N 次、用药 N 次 |
| `SET_MAX` | 将进度设置为当前值和事件值中的较大者 | 仓库最大材料数、最高记录 |
| `SAME_DAY_COUNT` | 同一天内累计，日期变化后重置 | 一天内开始任务 N 次 |
| `DAILY_STREAK` | 连续天数累计，中断后重置 | 连续使用 N 天 |
| `RESET` | 将进度重置为 0，不触发解锁 | 连续失败/连续未出货类成就的中断条件 |

## 条件匹配

### where

`where` 适合简单相等判断。

```json
"trigger": {
  "event": "subtask_error",
  "mode": "UNLOCK",
  "where": {
    "subtask": "CopilotTask"
  }
}
```

只有事件 payload 中 `subtask` 等于 `CopilotTask` 时才会触发。

### conditions

`conditions` 适合数值比较、区间、包含等判断。

```json
"trigger": {
  "event": "all_tasks_completed",
  "mode": "UNLOCK",
  "conditions": [
    {
      "field": "elapsedMillis",
      "op": "GTE",
      "value": "10800000"
    }
  ]
}
```

支持的 `op`：

| op | 含义 | 示例 |
|----|------|------|
| `EQ` | 等于 | `level == 6` |
| `NE` | 不等于 | `task != StartGameTask` |
| `GT` | 大于 | `level > 5` |
| `GTE` | 大于等于 | `elapsedMillis >= 10800000` |
| `LT` | 小于 | `level < 6` |
| `LTE` | 小于等于 | `count <= 3` |
| `BETWEEN` | 闭区间 | `hour` 在 `0..3` |
| `CONTAINS` | 包含字符串，忽略大小写 | 名称包含某文本 |
| `MONTH_DAY` | 月日匹配 | `04-01` |
| `MONTH_DAY_BETWEEN` | 月日区间匹配，支持跨年 | `12-24..01-03` |

## 内置事件

事件名定义在 `AchievementEvents` 中。新增成就时优先复用以下事件。

| event | 上报时机 | 常见 payload |
|-------|----------|--------------|
| `app_launch` | 应用启动 | 自动附加 `date`、`monthDay`、`hour`、`random`、`version` |
| `achievement_page_opened` | 打开成就页 | 无 |
| `error_log_opened` | 打开错误日志页 | 无 |
| `language_changed` | 切换界面语言 | 无 |
| `task_node_added` | 新增任务节点 | 无 |
| `task_node_removed` | 删除任务节点 | 无 |
| `mission_started` | 成功开始任务 | 自动附加 `date` |
| `task_stopped` | 手动停止任务 | 无 |
| `task_chain_error` | 任务链错误 | `taskchain` |
| `all_tasks_completed` | 所有任务完成 | `elapsedMillis` |
| `subtask_error` | SubTask 错误 | `subtask` |
| `process_task_started` | ProcessTask 开始 | `task` |
| `process_task_completed` | ProcessTask 完成 | `taskchain`、`task` |
| `subtask_extra_info` | SubTask 额外信息 | `what` 及对应附加字段 |
| `recruit_result` | 公招结果 | `level` |
| `medicine_used` | 使用理智药 | `isExpiring`、`expiringTotal` |
| `copilot_success` | Copilot 成功通关 | 无 |
| `copilot_liked` | 成功给作业点赞 | 无 |
| `schedule_saved` | 保存定时任务 | 无 |
| `update_completed` | 更新完成 | `channel`、`version` |
| `update_failed` | 更新失败 | `channel`、`message` |
| `toolbox_result` | 工具箱识别结果 | `type`、`value` |
| `mini_game_started` | 打开小游戏 | 无 |
| `log_exported` | 导出日志 | 无 |

仓库会给每个事件自动补充以下 payload：

| 字段 | 说明 |
|------|------|
| `date` | 当前日期，格式 `yyyy-MM-dd` |
| `monthDay` | 当前月日，格式 `MM-dd` |
| `hour` | 当前小时，`0` 到 `23` |
| `random` | 本次事件生成的 `0.0..1.0` 随机数 |
| `version` | 当前应用版本名 |

## 新增成就流程

### 1. 判断是否需要改 Kotlin

先确认新成就是否能复用已有事件。

如果能复用已有事件，只改 `achievements.json`。

如果不能复用，需要先在对应业务代码中新增事件上报：

```kotlin
achievementRepository.recordEvent(
    AchievementEvents.SomeEvent,
    mapOf("field" to "value"),
)
```

新增事件名时，同时在 `AchievementEvents` 中加常量。

### 2. 在 JSON 中添加成就对象

建议复制同类成就，再修改：

- `id`
- `title`
- `description`
- `condition`
- `category`
- `group`
- `target`
- `groupIndex`
- `trigger` 或 `triggers`

### 3. 选择触发模式

常见选择：

- 一次性解锁：`UNLOCK`
- 累计次数：`INCREMENT`
- 每日连续：`DAILY_STREAK`
- 同日累计：`SAME_DAY_COUNT`
- 满足中断条件时清零：`RESET`

### 4. 验证 JSON

至少确认：

- JSON 可解析。
- `id` 不重复。
- `category` 拼写正确。
- `trigger.event` 不为空。
- `trigger.mode` 拼写正确。
- 条件字段名与事件 payload 一致。

## 示例

### 打开成就页一次解锁

```json
{
  "id": "AchievementObserver",
  "title": {
    "zh": "成就观测者",
    "en": "Achievement Observer"
  },
  "description": {
    "zh": "你开始关注这些小小的里程碑。",
    "en": "You started watching these small milestones."
  },
  "condition": {
    "zh": "打开一次成就页面",
    "en": "Open the achievements page once"
  },
  "category": "FEATURE_EXPLORATION",
  "target": 0,
  "hidden": false,
  "rare": false,
  "releasePhase": 3,
  "trigger": {
    "event": "achievement_page_opened",
    "mode": "UNLOCK"
  }
}
```

### Copilot 成功 10 次

```json
{
  "id": "UseCopilot2",
  "title": {
    "zh": "一摆到底",
    "en": "Hands-Off All the Way"
  },
  "description": {
    "zh": "活动全交给牛牛打。",
    "en": "Leave all events to MAA."
  },
  "condition": {
    "zh": "使用 Copilot 通关 10 次",
    "en": "Clear 10 times with Copilot"
  },
  "category": "AUTO_BATTLE",
  "group": "UseCopilot",
  "target": 10,
  "groupIndex": 2,
  "releasePhase": 1,
  "trigger": {
    "event": "copilot_success",
    "mode": "INCREMENT"
  }
}
```

### 连续使用 7 天

```json
"trigger": {
  "event": "app_launch",
  "mode": "DAILY_STREAK"
}
```

### 一天内开始任务 3 次

```json
"trigger": {
  "event": "mission_started",
  "mode": "SAME_DAY_COUNT"
}
```

### 任务运行超过 3 小时

```json
"trigger": {
  "event": "all_tasks_completed",
  "mode": "UNLOCK",
  "conditions": [
    {
      "field": "elapsedMillis",
      "op": "GTE",
      "value": "10800000"
    }
  ]
}
```

### 公招连续未出 6 星，出 6 星后重置

```json
"triggers": [
  {
    "event": "recruit_result",
    "mode": "INCREMENT",
    "conditions": [
      {
        "field": "level",
        "op": "LT",
        "value": "6"
      }
    ]
  },
  {
    "event": "recruit_result",
    "mode": "RESET",
    "conditions": [
      {
        "field": "level",
        "op": "GTE",
        "value": "6"
      }
    ]
  }
]
```

## 各模块职责

### `AchievementModels.kt`

负责定义成就系统的数据结构。

主要内容：

- `AchievementDefinition`：JSON 中的一条成就定义。
- `AchievementTrigger`：触发规则。
- `AchievementTriggerMode`：触发后的处理方式。
- `AchievementCondition`：条件判断。
- `AchievementConditionOp`：条件操作符。
- `AchievementRecord`：持久化记录。
- `AchievementState`：UI 使用的成就状态。
- `AchievementEvents`：业务代码上报的事件名。

新增 JSON 字段时通常需要先改这里。

### `AchievementRepository.kt`

负责成就系统的核心逻辑。

主要职责：

- 从 `assets/achievements.json` 加载成就定义。
- 从 DataStore 加载和保存用户记录。
- 提供 `recordEvent(...)` 给业务模块上报事件。
- 根据 `trigger` 和 `triggers` 匹配事件。
- 根据 `where` 和 `conditions` 判断是否满足条件。
- 根据 `mode` 更新进度、解锁或重置。
- 通过 `unlockEvents` 发出新解锁事件。

旧的 `unlock(...)`、`addProgress(...)`、`setProgress(...)` 等方法保留为兼容入口，新代码优先使用 `recordEvent(...)`。

### `AchievementViewModel.kt`

负责成就页状态。

主要职责：

- 从 `AchievementRepository.achievements` 收集成就列表。
- 仅展示已解锁成就，并根据搜索文本过滤成就 ID。
- 维护总成就数和已解锁成就数。
- 打开成就页时上报 `achievement_page_opened` 事件。

### `AchievementView.kt`

负责成就页 UI。

主要职责：

- 展示成就标题、描述、条件、进度和解锁时间。
- 未解锁成就默认不出现在列表中。
- 保留锁定占位文本逻辑，用于其他入口复用未解锁状态时避免泄露内容。
- 格式化 `{key=...}` 占位符。

### `AppNavigation.kt`

负责全局解锁提示。

主要职责：

- 监听 `AchievementRepository.unlockEvents`。
- 弹出 Snackbar：`达成成就：xxx`。

### 业务模块

业务模块只负责上报事件，不应该关心具体成就 ID。

示例：

```kotlin
achievementRepository.recordEvent(AchievementEvents.CopilotSuccess)
```

带 payload 的示例：

```kotlin
achievementRepository.recordEvent(
    AchievementEvents.SubTaskError,
    mapOf("subtask" to subtask),
)
```

带数量的示例：

```kotlin
achievementRepository.recordEvent(
    AchievementEvents.MedicineUsed,
    mapOf("isExpiring" to isExpiring.toString()),
    count,
)
```

## 验证建议

修改成就配置后，建议至少做以下检查：

1. 解析 `achievements.json`，确认没有 JSON 语法错误。
2. 检查 `id` 是否重复。
3. 检查 `category`、`mode`、`op` 是否拼写正确。
4. 搜索新增 `event` 是否已经在 Kotlin 侧上报。
5. 手动触发一次对应行为，观察 Snackbar 和成就页是否新增已解锁条目。

可用 PowerShell 简单检查 JSON：

```powershell
$json = [System.IO.File]::ReadAllText("app\src\main\assets\achievements.json", [System.Text.Encoding]::UTF8)
$defs = $json | ConvertFrom-Json
"definitions=$($defs.Count)"
"duplicateIds=$(@($defs | Group-Object id | Where-Object Count -gt 1).Count)"
```

## 注意事项

- `id` 一旦发布后不要随意修改，否则已有用户的解锁记录无法对应。
- 只改 JSON 新增成就的前提是复用已有事件。
- 新事件需要先在 Kotlin 侧上报，然后才能被 JSON 使用。
- `category` 是 Kotlin 枚举，不能随意写新值。
- `target` 只对进度型模式有意义，例如 `INCREMENT`、`SAME_DAY_COUNT`、`DAILY_STREAK`、`SET_MAX`。
- `UNLOCK` 模式不需要 `target`。
- `RESET` 通常放在 `triggers` 中，与另一个 `INCREMENT` 规则配合使用。
- `RESET` 只负责清空进度，不会单独解锁成就。
- 未解锁成就默认不出现在成就页列表中。
- 隐藏成就 `hidden=true` 解锁前不应暴露真实标题、描述和条件。
- 修改 `achievements.json` 后需要重新构建应用；assets 中的 JSON 不是运行时热更新资源。
