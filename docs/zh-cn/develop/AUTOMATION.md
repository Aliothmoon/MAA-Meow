# 外部自动化集成

本文档介绍如何通过 Intent / `am` 命令从外部应用（如 MacroDroid、Tasker）触发 MaaMeow 执行指定任务配置。

## 使用场景

MaaMeow 内置的定时任务在锁屏状态下会跳过执行。如果你有以下需求，可以借助外部自动化工具实现更灵活的调度：

- 配合解锁/锁屏动作形成完整的无人值守挂机流程
- 将 MaaMeow 与其他自动化任务（如 OA 打卡）时分复用同一台设备
- 通过第三方调度系统精确控制任务启动时机

典型流程：

```
定时触发 → 解锁屏幕 → am start 启动指定配置 → MaaMeow 执行任务 → Webhook 回调 → 锁定屏幕
```

## 前提条件

| 条件 | 说明 |
|------|------|
| 运行模式 | 必须切换为**后台模式**（设置页 → 运行模式），前台模式不支持外部触发 |
| 应用状态 | 应用须在后台保持运行，Shizuku 服务已连接 |
| 执行权限 | `am` 命令需要 Root 权限，MacroDroid / Tasker 的 Shell 任务须勾选 Root 执行 |

## 第一步：获取 Profile ID

每个任务配置（Profile）都有一个唯一的 ID，外部触发时通过此 ID 指定目标配置。

1. 打开 MaaMeow，进入**后台任务**页
2. 切换到**配置管理**模式（点击 Profile 图标进入管理面板）
3. 找到目标配置，点击**编辑（铅笔）图标**
4. 展开区底部显示该配置的 ID，点击右侧复制图标即可复制完整 ID

Profile ID 是一串固定的 UUID，格式如：`3f4a1b2c-xxxx-xxxx-xxxx-xxxxxxxxxxxx`。

## 第二步：构造 am 命令

**基本用法**

```bash
am start \
  -a com.aliothmoon.maameow.action.LAUNCH_PROFILE \
  -n com.aliothmoon.maameow/.MainActivity \
  --es extra_profile_id "你的Profile-ID"
```

**强制启动**（中断当前正在运行的任务，立即切换并启动）

```bash
am start \
  -a com.aliothmoon.maameow.action.LAUNCH_PROFILE \
  -n com.aliothmoon.maameow/.MainActivity \
  --es extra_profile_id "你的Profile-ID" \
  --ez extra_force_start true
```

### 参数说明

| 参数 | 必填 | 说明 |
|------|------|------|
| `extra_profile_id` | 是 | 目标配置的 UUID |
| `extra_force_start` | 否 | `true` 表示强制中断当前任务后再启动，默认 `false` |

## MacroDroid 配置示例

1. 新建宏，添加所需触发器（如：定时、NFC、来电等）
2. 动作 → **Shell 脚本**，输入以下命令：

   ```
   am start -a com.aliothmoon.maameow.action.LAUNCH_PROFILE -n com.aliothmoon.maameow/.MainActivity --es extra_profile_id "你的Profile-ID"
   ```

3. 勾选 **Root 执行**
4. 如需任务完成后自动锁屏，配合 MaaMeow 的 **Webhook** 功能：在 MaaMeow 设置中填写 Webhook 地址，MacroDroid 监听该回调后执行锁屏动作

## Tasker 配置示例

1. 新建任务，添加动作 → **代码** → **运行 Shell**
2. 命令填入：

   ```
   am start -a com.aliothmoon.maameow.action.LAUNCH_PROFILE -n com.aliothmoon.maameow/.MainActivity --es extra_profile_id "你的Profile-ID"
   ```

3. 勾选 **使用 Root**

## 注意事项

- 触发后会有 **30 秒倒计时**，期间可在应用内点击「立即执行」跳过等待，或「取消」中止
- 若当前不是后台模式，触发将被拒绝并以 Toast 提示，不会自动切换模式
- 若有任务正在运行且未设置 `extra_force_start true`，新触发会被跳过并提示「系统繁忙」
- 建议在解锁屏幕后稍等 1～2 秒再执行 `am` 命令，确保应用已完成唤醒
