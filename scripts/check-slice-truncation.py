#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-slice-truncation.py  (G33 + G34 · PRD v0.33 §6.1)

G33 判据：禁止在「字段/列投影」相关代码里用 `.slice(0, N)` 截断字段集合。
        融合方案 R4 / W0-3 指出：旧 RuntimeDataManagement 用 `slice(0, 8)` 把第 9 列起
        的用户字段无声吞掉（列硬截断）。现列由 projectColumns 单一真源驱动，任何重新引入的
        截断都会再次制造「看不见也解释不了的列」。

G34 判据：deliveryMode 默认值全仓唯一（应为 RUNTIME=1）。
        旧默认值 GENERATIVE=0 会导致「新建实体默认进生成态而非运行态」，与 W0-6 抵触。
        凡出现字面量 `deliveryMode: 0` / `deliveryMode = 0` 一律视为回归。
        ⚠ 判定边界：用户显式选择「手写界面」的交互（UiModeCard onSelect）须使用
        常量 META_DELIVERY_GENERATIVE（metadata.ts），不再裸写 0，故不在本规则命中范围。

机器可验（变异验证）：
  · 在 RuntimeDataManagement.tsx 注入 `const x = fields.slice(0, 8);`  → G33 变红；
  · 在任意元数据文件注入 `deliveryMode: 0` → G34 变红。

用法：python3 scripts/check-slice-truncation.py [--root <repo-root>]
"""
import os
import re
import sys

META_APP_REL = "bone-frontend/apps/bone-metadata-app/src"
# 允许列表：仅「最近编辑 / 近期列表」这类 UI 展示截断可用 .slice(0, N)，与字段投影无关。
SLICE_ALLOWLIST = {
    os.path.join("utils", "recent.ts"),
    os.path.join("pages", "ModelingWorkspace.tsx"),
    os.path.join("pages", "EntityManagement.tsx"),  # B2 命令面板候选（cmdResults 最多 8 条）＝UI 展示截断，非列投影
}
SLICE_RE = re.compile(r"\.slice\(\s*0\s*,\s*\d+")
DELIVERY_ZERO_RE = re.compile(r"deliveryMode\s*[:=]\s*0\b")
DELIVERY_ONE_RE = re.compile(r"deliveryMode\s*[:=]\s*1\b")


def fail(msg):
    print(f"[G33] ❌ {msg}")
    sys.exit(1)


def iter_src(root):
    base = os.path.join(root, META_APP_REL)
    if not os.path.isdir(base):
        fail(f"找不到元数据前端源码目录：{META_APP_REL}（root={root}）")
    for dirpath, _dirs, files in os.walk(base):
        for fn in files:
            if fn.endswith((".ts", ".tsx")):
                yield os.path.join(dirpath, fn)


def main():
    root = None
    if "--root" in sys.argv:
        idx = sys.argv.index("--root")
        if idx + 1 < len(sys.argv):
            root = sys.argv[idx + 1]
    if root is None:
        here = os.path.dirname(os.path.abspath(__file__))
        root = (
            os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
            or os.path.dirname(here)
        )

    slice_violations = []
    delivery_zero = []
    delivery_one = 0

    for fpath in iter_src(root):
        rel = os.path.relpath(fpath, os.path.join(root, META_APP_REL))
        with open(fpath, "r", encoding="utf-8") as fh:
            content = fh.read()

        # G33：.slice(0, N) 截断禁令
        if SLICE_RE.search(content):
            if rel in SLICE_ALLOWLIST:
                print(f"  ⓘ 允许列表跳过 {rel} 的 .slice(0, N)（UI 近期列表截断，非字段投影）")
            else:
                for ln, line in enumerate(content.splitlines(), 1):
                    if SLICE_RE.search(line):
                        slice_violations.append(f"{rel}:{ln}  {line.strip()}")

        # G34：deliveryMode 默认值唯一性
        if DELIVERY_ZERO_RE.search(content):
            for ln, line in enumerate(content.splitlines(), 1):
                if DELIVERY_ZERO_RE.search(line):
                    delivery_zero.append(f"{rel}:{ln}  {line.strip()}")
        delivery_one += len(DELIVERY_ONE_RE.findall(content))

    ok = True

    print("── G33 列投影截断禁令 ──")
    if slice_violations:
        for v in slice_violations:
            print(f"  ❌ {v}")
        ok = False
    else:
        print("  ✅ 字段/列投影代码无 .slice(0, N) 截断")

    print("── G34 deliveryMode 默认值唯一性 ──")
    if delivery_zero:
        for v in delivery_zero:
            print(f"  ❌ {v}")
        ok = False
    else:
        print("  ✅ 无 deliveryMode=0（GENERATIVE）默认值残留")
    if delivery_one == 0:
        print("  ❌ 未发现任何 deliveryMode=1（RUNTIME）默认值（基线缺失）")
        ok = False
    else:
        print(f"  ✅ 存在 {delivery_one} 处 deliveryMode=1（RUNTIME）默认")

    if not ok:
        fail("G33/G34 未通过：存在列投影截断或 deliveryMode 默认值不一致回归。")
    print("[G33] ✅ 列投影截断禁令 + deliveryMode 默认值唯一性 通过")


if __name__ == "__main__":
    main()
