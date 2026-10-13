"""Build the GitHub Release body: download block on top, changelog below.

The block is generated from the APKs that are actually about to be uploaded,
so a link can never point at an asset that does not exist.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import quote

# mirrorchyan_release_note.yml 按这对标记剔除下载区块，改动需同步
BLOCK_START = "<!-- downloads:start -->"
BLOCK_END = "<!-- downloads:end -->"

MIRRORCHYAN_LINK = (
    "[已有 Mirror酱 CDK？前往 Mirror酱 高速下载]"
    "(https://mirrorchyan.com/zh/projects?rid=MAA&os=android&source=maagh-release)"
)

# 文件名 MaaMeow-<tag>-<label>.apk 里的 label，与 build-release.yml 的 matrix.label 一致
UNIVERSAL = "universal"
UNIVERSAL_TITLE = "通用版"
UNIVERSAL_NOTE = "手机、平板、模拟器都能装，不确定选哪个就下这个"

# 按架构拆分的包：(label, 表格里的名字, 适用设备)
# 序即表格行序，也是没有 universal 包时主链接的回退顺序
SPLIT_PACKAGES = [
    ("arm64-v8a", "arm64-v8a", "手机 / 平板"),
    ("x64", "x64", "仅 x86_64 模拟器（Windows 上的蓝叠 / MuMu / 雷电等），手机无法安装"),
    (
        "z-arm64-v8a-emu-compat",
        "arm64 模拟器兼容包",
        "arm64 模拟器（Mac 上的蓝叠 Air、MuMu Pro Mac 等）装通用包后启动任务即闪退时改用",
    ),
]
COMPAT_LABEL = "z-arm64-v8a-emu-compat"
COMPAT_NOTE = (
    "兼容包功能与 arm64-v8a 一致，只是关掉了 OpenCV 的 SVE2 指令分派。"
    "装了通用包闪退的模拟器用户请直接换这个包，不要再试通用包"
)


def collect(artifacts: Path, tag: str):
    """Return ({label: filename}, [unrecognized filenames])."""
    pattern = re.compile(rf"^MaaMeow-{re.escape(tag)}-(?P<label>.+)\.apk$")
    labels = {UNIVERSAL} | {label for label, _, _ in SPLIT_PACKAGES}

    found: dict[str, str] = {}
    unknown: list[str] = []
    for path in sorted(artifacts.iterdir()):
        if not path.is_file():
            continue
        match = pattern.match(path.name)
        if match and match["label"] in labels:
            found[match["label"]] = path.name
        else:
            unknown.append(path.name)
    return found, unknown


def build_block(found: dict[str, str], unknown: list[str], base_url: str, tag: str) -> str:
    def link(text: str, name: str) -> str:
        return f"[{text}]({base_url}/{quote(name)})"

    splits = [(label, title, note, name) for label, title, note in SPLIT_PACKAGES if (name := found.get(label))]

    # 只给一个主链接：几个包并排时手机用户会按「64 位」点到装不上的 x64 包
    lines = []
    if universal := found.get(UNIVERSAL):
        lines.append(f"**{link(f'下载 MaaMeow {tag}（{UNIVERSAL_TITLE}）', universal)}**：{UNIVERSAL_NOTE}")
    elif splits:
        _, title, note, name = splits.pop(0)
        lines.append(f"**{link(f'下载 MaaMeow {tag}（{title}）', name)}**：{note}")

    # 次要包折叠，摘要列出包名便于查找
    if splits:
        summary = "其他安装包（一般不需要）：" + " · ".join(title for _, title, _, _ in splits)
        lines += ["", "<details>", f"<summary>{summary}</summary>", ""]
        lines += ["| 安装包 | 适用设备 |", "| --- | --- |"]
        lines += [f"| {link(title, name)} | {note} |" for _, title, note, name in splits]
        if any(label == COMPAT_LABEL for label, _, _, _ in splits):
            lines += ["", COMPAT_NOTE]
        lines += ["", "</details>"]
    if unknown:
        lines += ["", "其他文件：" + " · ".join(link(name, name) for name in unknown)]
    lines += ["", MIRRORCHYAN_LINK]
    return "\n".join(lines).strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True, help="directory holding the release APKs")
    parser.add_argument("--tag", required=True, help="release tag, e.g. v0.22.0")
    parser.add_argument("--repo", required=True, help="owner/repo")
    parser.add_argument("--changelog", type=Path, required=True, help="changelog markdown file")
    parser.add_argument("--output", type=Path, required=True, help="release body file to write")
    args = parser.parse_args()

    found, unknown = collect(args.artifacts, args.tag)
    for name in unknown:
        # 不让未登记的产物卡住发版：照常发布，但在表格下方列出并提醒补表
        print(f"::warning::{name} 不在下载区的安装包定义里，已列入「其他文件」")
    if not found:
        print(f"::warning::{args.artifacts} 下没有可识别的安装包，下载区没有主链接")

    base_url = f"https://github.com/{args.repo}/releases/download/{quote(args.tag)}"
    block = build_block(found, unknown, base_url, args.tag)
    changelog = args.changelog.read_text(encoding="utf-8").strip()

    body = f"{BLOCK_START}\n\n{block}\n\n{BLOCK_END}\n\n{changelog}\n"
    args.output.write_text(body, encoding="utf-8", newline="\n")
    print(body)
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
