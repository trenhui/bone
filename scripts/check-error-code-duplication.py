#!/usr/bin/env python3
"""门禁：`COMMON_*` 公共错误码只能由 bone-core 的 `CommonErrorCodes` 定义。

背景
----
`CommonErrorCodes`（bone-core）的类注释已写明设计意图：跨模块共用的语义（无权限 / 不存在 / 校验失败…）
**必须集中在 bone-core 一处**，否则会退化成 N 个模块各有一套同义码，破坏错误码登记册的可聚合性。

但实测（2026-10-05）发现 6 个 `COMMON_*` 码值在多个模块**各自字面量重定义**：

    COMMON_FORBIDDEN            bone-core / bone-extension-studio / studio-generator
    COMMON_IDEMPOTENCY_CONFLICT bone-core / bone-blueprint / bone-extension-studio
    COMMON_INTERNAL_ERROR       bone-core / bone-extension-studio / studio-generator
    COMMON_NOT_FOUND            bone-core / studio-generator
    COMMON_PRECONDITION_FAILED  bone-core / bone-extension-studio
    COMMON_VALIDATION_FAILED    bone-core / bone-extension-studio / studio-generator

危害不是"多写了一个常量"，而是**修复时会漏改**：真源改了值或改名，副本还留着旧值 ⇒
同一语义对外出现两个码，前端 i18n 键与监控告警都会对不上。

为什么不在本门禁里直接删掉存量
--------------------------
存量收敛要把 21 处引用改成 `CommonErrorCodes.X`，且受一条硬约束限制：
`check-i18n-sync.py` 的 `CODE_RE` 只认**字面量**（`public static final String X = "COMMON_XXX"`），
把常量改成 `= CommonErrorCodes.XXX` 会让 i18n 门禁扫不到 ⇒ 判定「后端缺码」而阻断。
因此存量替换必须与 i18n 扫描口径一起改，属独立任务。

本门禁的职责是**阻止恶化**：新增的 `COMMON_*` 重复定义必须登记进基线，基线只可收缩。

用法：
    python3 scripts/check-error-code-duplication.py            # 报告
    python3 scripts/check-error-code-duplication.py --check     # 阻断（接 CI / pre-commit）
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "doc" / "architecture" / "error-code-duplication-baseline.json"

# 与 check-i18n-sync.py 的 CODE_RE 保持一致：只认字面量声明
CODE_RE = re.compile(r'public static final String\s+(\w+)\s*=\s*"(COMMON_[A-Z0-9_]+)"')


def scan() -> dict[str, list[str]]:
    """返回 {码值: ["模块#常量名", ...]}，含真源自身。"""
    by_value: dict[str, list[str]] = defaultdict(list)
    for path in sorted(ROOT.rglob("*ErrorCodes.java")):
        rel = path.relative_to(ROOT).as_posix()
        if "/target/" in rel:
            continue
        module = rel.split("/src/main/")[0]
        for name, value in CODE_RE.findall(path.read_text(encoding="utf-8", errors="ignore")):
            by_value[value].append(f"{module}#{name}")
    return by_value


def main() -> int:
    parser = argparse.ArgumentParser(description="COMMON_* 错误码重复定义门禁")
    parser.add_argument("--check", action="store_true", help="有未登记的重复定义则阻断")
    args = parser.parse_args()

    by_value = scan()
    known: dict[str, list[str]] = dict(
        (json.loads(BASELINE.read_text(encoding="utf-8")).get("known_duplicates") if BASELINE.exists() else {}) or {}
    )

    undeclared: dict[str, list[str]] = {}
    declared = 0
    for value, owners in sorted(by_value.items()):
        if len(owners) <= 1:
            continue
        known_owners = set(known.get(value, []))
        new_ones = [o for o in owners if o not in known_owners]
        if new_ones:
            undeclared[value] = new_ones
        else:
            declared += 1

    if declared:
        print(f"已登记的历史重复 {declared} 组（基线只可收缩）：")
        for value in sorted(k for k, v in known.items() if v and set(v) >= set(by_value.get(k, []))):
            print(f"  [基线] {value}: {', '.join(known[value])}")

    if undeclared:
        print(f"\n⚠️ 未登记的 COMMON_* 重复定义（新增 {len(undeclared)} 组）：", file=sys.stderr)
        for value, owners in undeclared.items():
            print(f"  [新增] {value}: {', '.join(owners)}", file=sys.stderr)
        print(
            "\nCOMMON_* 只能由 bone-core 的 CommonErrorCodes 定义。\n"
            "  · 若要新增 COMMON_ 码：先在 CommonErrorCodes 登记，再由模块引用，不要重写字面量；\n"
            f"  · 确属历史重复：登记进 {BASELINE.relative_to(ROOT).as_posix()}（只可收缩，修复即删除）。",
            file=sys.stderr,
        )
        return 1 if args.check else 0

    print(f"OK：未发现未登记的 COMMON_* 重复定义（已登记 {declared} 组历史重复）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
