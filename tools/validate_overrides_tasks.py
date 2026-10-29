#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""打包前静态校验 overrides/resource/tasks/tasks.json。

存在的意义：MAA Core 在 LoadResource 阶段解析单个任务，字段非法时**静默丢弃该任务**
（返回 nullptr，只往 log 里写一行），后果是 Custom 任务查不到节点名 →
`任务加入队列失败：Custom` / `没有任务成功加入队列，已取消启动`。
最典型的一次事故：`"algorithm": "TemplateMatch"`（正确写法是 `MatchTemplate`），
116 个节点被整体丢弃，App 侧只看到 AppendTask 返回 0。

用法：
    python tools/validate_overrides_tasks.py [tasks.json]
退出码非 0 表示存在硬错误。
"""

import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_TASKS = os.path.join(ROOT, "app/src/main/assets/overrides/resource/tasks/tasks.json")
TEMPLATE_DIRS = [
    os.path.join(ROOT, "app/src/main/assets/overrides/resource/template"),
    os.path.join(ROOT, "app/src/main/assets/MaaSync/MaaResource/template"),
]

# MAA ResourceLoader 承认的算法名（libMaaCore.so 内的字符串常量）
ALGORITHMS = {
    "MatchTemplate",
    "OcrDetect",
    "JustReturn",
    "FeatureMatch",
    "ColorMatch",
    "HSVCount",
    "HSVCompare",
    "MultiScaleTemplateMatch",
}
# MAA TaskData::Action
ACTIONS = {"DoNothing", "ClickSelf", "ClickRect", "Swipe", "Stop", "Scroll"}
# 官方 tasks.json 里出现过的字段名，其余一律视为拼写错误
KNOWN_KEYS = {
    "Doc", "Doc2", "Doc3", "Docs", "doc",
    "action", "algorithm", "baseTask", "binThreshold", "cache",
    "colorScales", "colorScales_Doc", "colorWithClose", "count", "detector",
    "exceededNext", "fullMatch", "highResolutionSwipeFix", "isAscii",
    "maskRange", "maxTimes", "maxTimesDoc", "method", "next", "nmsDistance",
    "ocrReplace", "onErrorNext", "orderBy", "postDelay", "preDelay",
    "pureColor", "rectMove", "reduceOtherTimes", "roi", "specialParams",
    "specificRect", "sub", "templThreshold", "template", "text",
    "useRaw", "withoutDet",
}
ROI_KEYS = {"roi", "specificRect", "rectMove"}
NEXT_KEYS = {"next", "onErrorNext", "exceededNext", "sub", "reduceOtherTimes", "baseTask"}


def load(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise SystemExit("tasks.json 顶层必须是对象")
    return data


def num_list(v, n):
    return isinstance(v, list) and len(v) == n and all(
        isinstance(x, (int, float)) and not isinstance(x, bool) for x in v
    )


def build_template_index():
    """MAA 递归扫描 template/<client>/<模块>/ 目录并按**文件名**索引，
    所以引用写的是 `JieGarden@Roguelike@Xxx.png`，实际文件在子目录里。"""
    index = set()
    for d in TEMPLATE_DIRS:
        if not os.path.isdir(d):
            continue
        for dirpath, _, files in os.walk(d):
            for f in files:
                index.add(f)
    return index


TEMPLATES = build_template_index()


def template_exists(name):
    return os.path.basename(name) in TEMPLATES


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_TASKS
    tasks = load(path)
    errors, warnings = [], []

    for name, t in tasks.items():
        where = f"[{name}]"
        if not isinstance(t, dict):
            errors.append(f"{where} 不是对象")
            continue

        for k in t:
            if k not in KNOWN_KEYS:
                errors.append(f"{where} 未知字段 {k!r}（大概率拼写错误，Core 会忽略）")

        algo = t.get("algorithm")
        if algo is not None and algo not in ALGORITHMS:
            errors.append(f"{where} algorithm={algo!r} 非法，合法值 {sorted(ALGORITHMS)}")

        action = t.get("action")
        if action is None:
            # MAA 的 TaskData 缺省 action 就是 DoNothing，纯路由节点可以不写
            warnings.append(f"{where} 未写 action（Core 默认为 DoNothing）")
        elif action not in ACTIONS:
            errors.append(f"{where} action={action!r} 非法，合法值 {sorted(ACTIONS)}")

        if action == "ClickRect" and not num_list(t.get("specificRect"), 4) \
                and not num_list(t.get("rect"), 4):
            errors.append(f"{where} ClickRect 但缺少 specificRect/rect(4 元素)")

        for k in ROI_KEYS:
            if k in t and not num_list(t[k], 4):
                errors.append(f"{where} {k}={t[k]!r} 必须是 4 个数字")

        th = t.get("templThreshold")
        if th is not None and not (isinstance(th, (int, float)) and 0 <= th <= 1):
            errors.append(f"{where} templThreshold={th!r} 必须在 0~1")

        tpl = t.get("template")
        if tpl is not None:
            names = [tpl] if isinstance(tpl, str) else tpl
            if not isinstance(names, list) or not names:
                errors.append(f"{where} template 必须是字符串或字符串数组")
            else:
                for n in names:
                    if not isinstance(n, str):
                        errors.append(f"{where} template 元素不是字符串")
                    elif n in ("empty.png", ""):
                        pass  # 官方用作「不做匹配」的哨兵
                    elif not template_exists(n):
                        # 本功能自带的模板必须随仓库提交，缺了就是漏文件 -> 硬错误；
                        # 官方模板（MaaResource）是运行时才下载到 MaaSync/ 的，干净克隆里
                        # 本来就没有，缺了只提醒，否则 CI / 新克隆校验必然红。
                        if os.path.basename(n).startswith("MeterRewind"):
                            errors.append(
                                f"{where} 模板 {n} 在 overrides/resource/template 里不存在"
                                "（本功能自带模板必须随仓库提交）"
                            )
                        else:
                            warnings.append(
                                f"{where} 官方模板 {n} 未在本地找到"
                                "（由 MaaResource 运行时提供，无关本次改动）"
                            )

        text = t.get("text")
        if algo == "OcrDetect":
            if not isinstance(text, list) or not text:
                errors.append(f"{where} OcrDetect 但 text 为空")
            elif not all(isinstance(x, str) for x in text):
                errors.append(f"{where} text 元素必须全是字符串")

        for k in NEXT_KEYS:
            v = t.get(k)
            if v is None:
                continue
            refs = [v] if isinstance(v, str) else v
            if not isinstance(refs, list):
                errors.append(f"{where} {k} 必须字符串或数组")
                continue
            for r in refs:
                if not isinstance(r, str):
                    errors.append(f"{where} {k} 元素不是字符串")
                elif r.startswith(("#", "@")) or r in ("", "Stop"):
                    continue
                elif r not in tasks:
                    errors.append(f"{where} {k} 引用了不存在的任务 {r!r}")

        if action == "Stop":
            warnings.append(f"{where} 用了 Stop：会终结整个队列")

    # 电表倒转入口自检
    base = "JieGarden@Roguelike@MeterRewind"
    if base not in tasks:
        errors.append(f"缺少入口任务 {base}")
    # v20 起入口按轮次分叉：H=投衡轮（收下源石锭），L=投厉轮（要酒一壶），
    # v21 增加 R=投厉轮（要兵器一对），各 1..4 号
    for v in ("H", "L", "R"):
        for i in range(1, 5):
            if f"{base}@{v}Enter{i}" not in tasks:
                errors.append(f"缺少入口 {base}@{v}Enter{i}（Kotlin 会下发这个任务名）")
    # 三条链的投钱节点必须各自存在且文字不同，否则「5 衡 1 厉」会退化回只投衡
    if tasks.get(f"{base}@HPickHeng", {}).get("text") == tasks.get(f"{base}@LPickLi", {}).get("text"):
        errors.append("HPickHeng 与 LPickLi 的识别文字相同，轮次无法区分")
    # L 链拿烛火、R 链拿藏品：两张奖励卡的模板必须不同，否则「换藏品」会变成重复拿烛火
    l_reward = tasks.get(f"{base}@LTakeReward", {})
    r_reward = tasks.get(f"{base}@RTakeReward", {})
    if l_reward.get("template") == r_reward.get("template"):
        errors.append("LTakeReward 与 RTakeReward 的模板相同，刷钱与换藏品无法区分")

    for w in warnings:
        print(f"WARN  {w}")
    for e in errors:
        print(f"ERROR {e}")
    print(f"\n共 {len(tasks)} 个任务，{len(errors)} 个错误，{len(warnings)} 个警告")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
