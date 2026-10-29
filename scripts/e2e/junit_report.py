#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JUnit 汇总卡 —— 把「一堆 XML 目录」变成「一眼看出哪个套件红了、哪条断言红了」。

为什么需要它
------------
`BONE_E2E_JUNIT_DIR` 让每个套件各写一个 XML（避免互相覆盖，见 e2e_lib/report.py），
但代价是：一次 CI 跑完 11 个 Python + 20 个探针 = 31 个文件，人要在目录里翻。
CI 平台能渲染单文件，却**不负责跨套件的总账**，而「这轮到底几条红、红在哪」
恰恰是值班时第一个要回答的问题。本脚本只做这一件事：把目录聚成一张卡。

用法::

    python3 scripts/e2e/junit_report.py                       # 读 $BONE_E2E_JUNIT_DIR（缺省 /tmp/bone-e2e-junit）
    python3 scripts/e2e/junit_report.py --dir /tmp/e2e-junit
    python3 scripts/e2e/junit_report.py --md /tmp/e2e-report.md
    python3 scripts/e2e/junit_report.py --strict              # 有失败则退出码 1（供独立门禁用）

退出码：默认 **恒 0**（汇总器不制造失败，失败由各用例自己的退出码表达）；
`--strict` 下有失败才为 1。这样它可以安全地挂在 run.sh 末尾做「收尾播报」。
"""
import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET

DEFAULT_DIR = os.environ.get("BONE_E2E_JUNIT_DIR", "/tmp/bone-e2e-junit")


def _iter_suites(root):
    """兼容两种根：`<testsuites>`（多套件）与裸 `<testsuite>`（单套件）。"""
    if root.tag == "testsuite":
        yield root
        return
    for child in root.iter("testsuite"):
        yield child


def collect(directory):
    """读目录下全部 XML，返回 [(套件名, tests, failures, [失败明细])]。"""
    rows = []
    for path in sorted(glob.glob(os.path.join(directory, "*.xml"))):
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            # 坏 XML 也算一条「红」：静默跳过会把解析失败伪装成「没跑」
            rows.append((os.path.basename(path), 0, 1, [f"XML 解析失败: {exc}"]))
            continue
        for suite in _iter_suites(root):
            name = suite.get("name") or os.path.basename(path)
            tests = int(suite.get("tests") or 0)
            details = []
            for case in suite.iter("testcase"):
                for tag in ("failure", "error"):
                    node = case.find(tag)
                    if node is None:
                        continue
                    msg = (node.get("message") or node.text or "").strip()
                    details.append(f"{case.get('name')} :: {msg[:200]}")
            failures = int(suite.get("failures") or 0) + int(suite.get("errors") or 0)
            # 以**明细条数**为准兜底：套件属性缺失/不一致时，实际红条才是真相
            failures = max(failures, len(details))
            rows.append((name, tests, failures, details))
    return rows


def render_text(rows, directory):
    total = sum(r[1] for r in rows)
    failed = sum(r[2] for r in rows)
    lines = [
        "",
        "=" * 78,
        f"E2E JUnit 汇总卡（{directory}）",
        "=" * 78,
        f"套件 {len(rows)} 个 · 断言 {total} 条 · 失败 {failed} 条",
        "",
        f"{'套件':44s} {'断言':>6s} {'失败':>6s}",
        "-" * 78,
    ]
    for name, tests, failures, _ in rows:
        flag = "✗" if failures else "✓"
        lines.append(f"{flag} {name:42s} {tests:6d} {failures:6d}")
    if failed:
        lines.append("")
        lines.append("失败明细：")
        for name, _, failures, details in rows:
            if not failures:
                continue
            for d in details:
                lines.append(f"  · [{name}] {d}")
    elif rows:
        lines.append("")
        lines.append("全部通过。")
    else:
        lines.append("")
        lines.append(f"（目录内没有 XML：本轮未设 BONE_E2E_JUNIT_DIR 或用例未产出结果）")
    return "\n".join(lines)


def render_md(rows, directory):
    total = sum(r[1] for r in rows)
    failed = sum(r[2] for r in rows)
    out = [
        f"# E2E 汇总（{directory}）",
        "",
        f"- 套件 **{len(rows)}** 个 · 断言 **{total}** 条 · 失败 **{failed}** 条",
        "",
        "| 套件 | 断言 | 失败 |",
        "|---|---:|---:|",
    ]
    for name, tests, failures, _ in rows:
        out.append(f"| {'❌' if failures else '✅'} {name} | {tests} | {failures} |")
    if failed:
        out += ["", "## 失败明细", ""]
        for name, _, failures, details in rows:
            if not failures:
                continue
            for d in details:
                out.append(f"- `[{name}]` {d}")
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser(description="JUnit 目录 → 汇总卡")
    ap.add_argument("--dir", default=DEFAULT_DIR, help=f"JUnit 目录（默认 {DEFAULT_DIR}）")
    ap.add_argument("--md", help="同时写出 Markdown 汇总卡到该路径")
    ap.add_argument("--strict", action="store_true", help="有失败时退出码 1（供独立门禁用）")
    args = ap.parse_args()

    if not os.path.isdir(args.dir):
        print(f"目录不存在：{args.dir}（未跑过用例或未设 BONE_E2E_JUNIT_DIR）")
        return 0 if not args.strict else 1

    rows = collect(args.dir)
    print(render_text(rows, args.dir))
    if args.md:
        with open(args.md, "w", encoding="utf-8") as fh:
            fh.write(render_md(rows, args.dir))
        print(f"\nMarkdown 汇总已写出：{args.md}")

    failed = sum(r[2] for r in rows)
    return 1 if (args.strict and failed) else 0


if __name__ == "__main__":
    raise SystemExit(main())
