#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""分页约定观测门禁（诊断 #9：分页入参 web/application 双层去重）。

背景
----
分页是**技术/传输关注点**，应只有唯一真源。本仓已有 ``bone-core`` 的 ``PageParam``（含
``page``/``size`` + ``@Min`` 校验 + 默认值 + ``Serializable``）与 ``SortablePageParam``。但实测存在三类偏离：

* **R1 应用层查询未复用真源**：大量 ``XxxPageQuery`` 自身内联重写 ``page``/``size``、未 ``extends
  PageParam`` ⇒ 丢失集中校验/序列化，且与 web 层入参形成"双份定义"。
* **R2 命名族分裂**：存在 ``pageNum``/``pageSize`` 与 ``page``/``size`` 两族，前端/SDK 需按模块记忆。
* **R3 web 层重复声明**：``adapter/web`` 下的分页 Request 自行内联 ``page``/``size``，与 application
  查询对象重复（同名不同层的"双层"问题）。

设计（业界 ratchet 模式 + 仓库既有范式）
----------------------------------------
* **先量化、后收敛**：默认输出**全量存量清单**（按规则/模块分组），供决策"何时转阻断"。
* **ratchet**：``--baseline`` 固化存量；``--check`` 仅对**基线外新增**违规失败 ⇒ 存量只减不增，
  符合"不大爆炸重构 legacy、只对新增立规、存量随改动顺手收敛"。
* **report-only 草案**：默认恒 ``exit 0``，**不**接入 ``scripts/check.sh``/``ci-check.sh``（L3 门禁
  须架构师在 ``gate-state.json`` 登记并接入载体后方可阻断）。

用法::

    python3 scripts/check-paging-convention.py# report-only：全量存量清单，恒 exit 0
    python3 scripts/check-paging-convention.py --baseline  # 固化当前存量基线
    python3 scripts/check-paging-convention.py --check     # 仅基线外新增违规才 exit 1
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO / "doc/architecture/paging-convention-baseline.json"

SCAN_ROOTS = ["bone-platform", "bone-engine", "bone-blueprint", "bone-framework"]

# 分页命名族：page/size 为规范；pageNum/pageSize 为分裂族。
CANON = {"page", "size"}
SPLIT = {"pageNum", "pageSize"}
PAGE_FIELDS = CANON | SPLIT

# 分页类型：XxxPageQuery / PageQry / PageRequest / PageParam
TYPE_RE = re.compile(
    r"\b(?:public\s+|abstract\s+|final\s+)*(class|record)\s+(\w*Page(?:Query|Qry|Request|Param))\b"
)
# 类型声明行内是否 extends PageParam/SortablePageParam
EXTENDS_RE = re.compile(r"\bextends\s+([\w.]+(?:\s*,\s*[\w.]+)*)")
# 字段声明：private/protected Integer page; 等
FIELD_RE = re.compile(
    r"\b(?:private|protected|public)\s+(?:final\s+)?(?:Integer|int|Long|long)\s+("
    + "|".join(PAGE_FIELDS)
    + r")\b"
)
# record 组件：Integer page, Integer size（组件类型+名字）
COMP_RE = re.compile(r"\b(?:Integer|int|Long|long)\s+(" + "|".join(PAGE_FIELDS) + r")\s*(?:,|$|=|;|\))")


def strip_code_keep_lines(text: str) -> str:
    """剥离注释与字符串字面量，且输出行数与输入一一对应（保持行号）。

    禁用 ``re.sub(re.S)``：实测会跨文件吞掉注释→门禁假绿。逐字符状态机保持行数守恒。
    """
    out = []
    in_block = in_str = in_chr = False
    quote = ""
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 2
                out.append("  ")
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if in_str or in_chr:
            q = quote
            if c == "\\" and nxt:
                out.append("  ")
                i += 2
                continue
            if c == q:
                in_str = in_chr = False
                quote = ""
                out.append('" ' if q == '"' else "' ")
                i += 1
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if c == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if c == "/" and nxt == "*":
            in_block = True
            i += 2
            out.append("  ")
            continue
        if c == '"':
            in_str = True
            quote = '"'
            out.append('" ')
            i += 1
            continue
        if c == "'":
            in_chr = True
            quote = "'"
            out.append("' ")
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def extends_page_param(header: str) -> bool:
    m = EXTENDS_RE.search(header)
    if m and re.search(r"\b(SortablePageParam|PageParam)\b", m.group(1)):
        return True
    return False


def record_components(code: str, offset: int) -> str:
    """record 组件列表：从类型声明处按括号配平提取（组件常跨行，不能只看声明行）。"""
    m = re.match(
        r"\s*(?:public\s+|abstract\s+|final\s+)*(?:class|record)\s+\w*Page(?:Query|Qry|Request|Param)\b",
        code[offset:],
    )
    if not m:
        return ""
    open_idx = offset + m.end()
    depth = 0
    for i in range(open_idx, len(code)):
        c = code[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return code[open_idx + 1 : i]
    return ""


def scan():
    import os

    violations = []
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for dirpath, _dirs, files in os.walk(base):
            dp = Path(dirpath)
            if "target" in dp.parts or "test" in dp.parts:
                continue
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                path = dp / fn
                rel = str(path.relative_to(REPO))
                code = strip_code_keep_lines(path.read_text(encoding="utf-8", errors="ignore"))
                lines = code.split("\n")

                # 收集本文件所有分页类型声明（名称、行号、kind、是否 extends PageParam）
                types = []
                for m in TYPE_RE.finditer(code):
                    line_no = code[: m.start()].count("\n") + 1
                    types.append(
                        {
                            "name": m.group(2),
                            "kind": m.group(1),
                            "line": line_no,
                            "extends": extends_page_param(code[m.start():]),
                            "offset": m.start(),
                        }
                    )
                if not types:
                    continue

                # 分页字段归属到最近的上方类型声明
                field_owner = {}
                for idx, line in enumerate(lines, start=1):
                    fm = FIELD_RE.search(line)
                    if fm:
                        # 归属到最近的上方分页类型声明
                        cand = [t for t in types if t["line"] <= idx]
                        if cand:
                            owner = cand[-1]
                            field_owner.setdefault((owner["name"], owner["line"]), set()).add(fm.group(1))
                # record 组件（可能跨行）：按括号配平提取后判定分页字段
                for t in types:
                    if t["kind"] == "record":
                        comp = record_components(code, t["offset"])
                        for cm in COMP_RE.finditer(comp):
                            field_owner.setdefault((t["name"], t["line"]), set()).add(cm.group(1))

                for t in types:
                    fields = field_owner.get((t["name"], t["line"]), set())
                    if not fields:
                        continue
                    is_web = "/adapter/web/" in "/" + rel
                    is_app = "/application/" in "/" + rel

                    # R2 命名族分裂
                    if fields & SPLIT:
                        violations.append(
                            {
                                "rule": "R2-naming",
                                "file": rel,
                                "line": t["line"],
                                "detail": f"{t['name']} 使用分裂命名族 {sorted(fields & SPLIT)}（规范为 page/size）",
                            }
                        )
                    # R1 应用层查询未复用 PageParam
                    if is_app and not t["extends"]:
                        violations.append(
                            {
                                "rule": "R1-app-no-true-source",
                                "file": rel,
                                "line": t["line"],
                                "detail": f"{t['name']} 未 extends PageParam/SortablePageParam 且内联 {sorted(fields & CANON)}",
                            }
                        )
                    # R3 web 层重复声明分页
                    if is_web and (fields & CANON):
                        violations.append(
                            {
                                "rule": "R3-web-duplicate",
                                "file": rel,
                                "line": t["line"],
                                "detail": f"{t['name']} 在 web 层重复声明分页 {sorted(fields & CANON)}（应复用 PageParam）",
                            }
                        )

    violations.sort(key=lambda v: (v["file"], v["line"], v["rule"]))
    return violations


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="基线外新增违规才 exit 1")
    ap.add_argument("--baseline", action="store_true", help="固化当前存量基线")
    args = ap.parse_args()

    violations = scan()

    if args.baseline:
        BASELINE_PATH.write_text(
            json.dumps(
                {
                    "note": "存量基线，只可收缩（ratchet）。修复后应移除对应条目。",
                    "rule": "分页唯一真源 PageParam + page/size 命名族",
                    "violations": violations,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print(f"基线已固化：{len(violations)} 条 -> {BASELINE_PATH.relative_to(REPO)}")
        return 0

    # 分组统计
    by_rule = {}
    for v in violations:
        by_rule.setdefault(v["rule"], []).append(v)

    print("=" * 72)
    print("分页约定观测门禁（#9 report-only，唯一真源 PageParam + page/size）")
    print(f"  偏离总数: {len(violations)}")
    for rule, items in sorted(by_rule.items()):
        print(f"    {rule}: {len(items)}")
    print("=" * 72)

    # 明细（限量，避免刷屏）
    for rule, items in sorted(by_rule.items()):
        print(f"\n[{rule}]")
        for v in items[:40]:
            print(f"  {v['file']}:{v['line']}  {v['detail']}")
        if len(items) > 40:
            print(f"  … 其余 {len(items) - 40} 条见 --baseline/--check 输出")

    if args.check:
        base = set()
        if BASELINE_PATH.exists():
            data = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
            base = {f"{e['file']}::{e['line']}::{e['rule']}" for e in data.get("violations", [])}
        new = [v for v in violations if f"{v['file']}::{v['line']}::{v['rule']}" not in base]
        if new:
            print(f"\n❌ 基线外新增偏离 {len(new)} 条（不得加入 baseline）:")
            for v in new:
                print(f"    {v['file']}:{v['line']}  {v['detail']}")
            return 1
        print("\n✅ 无基线外新增偏离（存量只可收缩）")
    else:
        print("\n(report-only：默认不阻断。转阻断需架构师登记 gate-state.json 并接入 check.sh)")
    return 0


if __name__ == "__main__":
    sys.exit(main())