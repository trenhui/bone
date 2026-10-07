#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁：对外分页入参类必须继承 bone-core 的分页真源（PageParam / SortablePageParam）。

背景
----
分页是**传输关注点**，不是领域逻辑 ⇒ 应有唯一真源，不允许每层重声明字段。
``bone-core/model/PageParam``（``page``/``size`` + ``@Min(1)``）与
``SortablePageParam``（+ 类型化 ``sortingFields``）已是唯一真源。

实测（2026-10-07）漂移证据：
- ``blueprint/adapter/web/dto/request/OrderPageRequest`` 内联 ``page``/``size``
  且带 ``@Max(100)``，而 ``PageParam`` **无上限** ⇒ 两份定义需手工同步，
  任一处改动都要记得改另一处，无机制发现不一致。
- 对外分页入参类共 **36** 个，其中 **30** 个是裸 class（自带 ``page``/``size``
  却不继承真源），仅** 4** 个继承了 ``PageParam``/``SortablePageParam``。

两类分页范式（**并列真源，不是违规，但要各归其位**）
--------------------------------------------------
1. **offset**（``page``/``size``）⇒ 应继承 ``PageParam``；
2. **cursor**（``cursor``/``limit``）⇒ 游标（keyset）分页，大表深翻用；
   出参配``PageResult.cursorOf``（已实现，会把 ``total/page/pages`` 置 null 并设 ``hasNext``）。
   入参侧真源：``bone-core`` 的 ``CursorPageParam``（2026-10-07 落地，此前为**真空**）。

⚠️ 判据陷阱一：``limit`` **不等于**游标分页
------------------------------------------
本脚本第二版曾把「出现 ``limit``」直接归为 cursor 族，**这是错的**（负向判据过宽导致假豁免）：
- ``system`` 的 ``LogExportQuery.limit`` —— 注释明写「导出不带分页」，是**导出行数上限**；
- ``studio-generator`` 的 ``LoadTablesQuery.limit`` —— 是**结果集截断**，防上千张表一次性回传。

二者都**没有 cursor 字段**，语义是「单次查询的规模上限」，**不是分页范式**。
把它们算作 cursor 族，会让真正需要收口的 cursor 入参（extension-studio 的 base64 id 游标）
继续以「语义正当」为由逃过门禁。故现行判据：**只有 ``cursor`` 字段才算 cursor 族**，
``bounded``（仅 ``limit``）单列为非分页范式、不判违规。

规则
----
对外分页入参类（类名匹配 ``*Qry|Query|PageQuery|PageReq|Request|DTO`` 且携带分页字段）
- **offset 族**：必须 ``extends PageParam`` 或 ``extends SortablePageParam``；
- **cursor 族**：🟡 只报告，待``CursorPageParam`` 真源落地后转阻断。

⚠️ 判据陷阱（本脚本第一版实测踩过，勿改回）
-------------------------------------------
**「子类体内有分页字段」不能作为「分页入参类」的判据**：
继承 ``PageParam`` 的类，其 ``page``/``size`` **在基类里**，子类体内没有这俩字段
⇒用「子类体内必须扫到分页字段」筛类，会把所有**已合规**的继承类整批过滤掉
⇒ 报出「0 个合规」，即**假绿**（与 grep 实测的 4 个合规类直接矛盾）。
正确顺序：**先按类头``extends`` 判合规并直接收下**，不要求子类体内有字段；
只有「未继承」的类才去扫字段以判定它是 offset 还是 cursor 族。

存量baseline 只可收缩：新增违规不得加入；条目被修复后应从 baseline 移除。

用法::

    python3 scripts/check-paging-param-ssot.py                    # 阻断（新增违规）
    python3 scripts/check-paging-param-ssot.py --report-only      # 只出报告，恒 exit 0
    python3 scripts/check-paging-param-ssot.py --update-baseline  # 按实测重写 baseline
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO / "doc/architecture/paging-param-ssot-baseline.json"

SCAN_ROOTS = ["bone-platform", "bone-engine", "bone-blueprint", "bone-framework"]
SKIP_DIRS = {"target", "node_modules", "dist", "build", ".git", "generated-code"}
# SDK 内部命名（FluentQuery.page(int pageNum,int pageSize)、Criteria.page()）是公共 API，不在射程内
SDK_MARKERS = ("bone-metadata-sdk", "/sdk/")

# 匹配 class/record/interface 声明；group(2)=类头（含 extends/implements），用于判继承
DECL_RE = re.compile(
    r"(?:public\s+|final\s+|abstract\s+|)*(?:class|record|interface)\s+(\w+)([^{]*)\{"
)
# 对外入参类命名特征
SUFFIX_RE = re.compile(r"(Qry|Query|PageQuery|PageReq|Request|DTO)$")
FIELD_DECL_RE = re.compile(
    r"^\s*private\s+(?:final\s+)?[\w<>,.\[\]\s?]+?\s+(\w+)\s*[=;]", re.MULTILINE
)
EXTENDS_RE = re.compile(r"extends\s+(SortablePageParam|PageParam|CursorPageParam)\b")


def paging_fields(block: str) -> set[str]:
    out = set()
    for m in FIELD_DECL_RE.finditer(block):
        low = m.group(1).lower()
        if any(k in low for k in ("page", "size", "limit", "cursor")):
            out.add(m.group(1))
    return out


def scan():
    rows = []
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for p in base.rglob("*.java"):
            if SKIP_DIRS & set(p.parts):
                continue
            if "src" not in p.parts or "test" in p.parts:
                continue
            rel = str(p.relative_to(REPO))
            if any(m in rel for m in SDK_MARKERS):
                continue
            text = p.read_text(encoding="utf-8", errors="ignore")
            decls = list(DECL_RE.finditer(text))
            for i, m in enumerate(decls):
                cname, header = m.group(1), m.group(2)
                if not SUFFIX_RE.search(cname):
                    continue
                # ✅ 关键顺序：先判继承并直接收下（分页字段在基类），不去要求子类体内有字段
                if EXTENDS_RE.search(header):
                    continue
                block = text[m.end() : decls[i + 1].start() if i + 1 < len(decls) else len(text)]
                fields = paging_fields(block)
                if not fields:
                    continue
                # ★ 分类判据（2026-10-07 修正）：**只有 `cursor` 才是游标范式**。
                # 此前本脚本把「出现 limit」也归为 cursor 族，实测是错的：
                #   LogExportQuery.limit 是「导出行数上限」（注释明说导出不带分页），
                #   LoadTablesQuery.limit 是「结果集截断」，
                # 二者都没有 cursor 字段，都不是游标分页范式，而是「单次查询的规模上限」。
                # 混为一谈会让真正的 cursor 范式（extension-studio 的 base64 id游标）
                # 继续以「语义正当」为由逃过收口，而真正该看的 limit 类被错误豁免。
                fam = "cursor" if "cursor" in fields else "bounded"
                rows.append({"file": rel, "class": cname, "family": fam,
                             "fields": sorted(fields)})
    rows.sort(key=lambda r: (r["family"], r["file"], r["class"]))
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument("--update-baseline", action="store_true")
    args = ap.parse_args()

    rows = scan()
    offset = [r for r in rows if r["family"] == "offset"]
    cursor = [r for r in rows if r["family"] == "cursor"]
    bounded = [r for r in rows if r["family"] == "bounded"]

    if args.update_baseline:
        BASELINE_PATH.write_text(
            json.dumps(
                {
                    "note": "offset 裸 class 存量，只可收缩。cursor 族待 CursorPageParam 落地后接管。",
                    "rule": "对外分页入参类必须 extends PageParam/SortablePageParam",
                    "offsetViolations": offset,
                },
                ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"baseline 已更新：offset {len(offset)} 条（cursor {len(cursor)}/ bounded {len(bounded)} 仅报告）")
        return 0

    baseline: set[str] = set()
    if BASELINE_PATH.exists():
        data = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
        baseline = {f"{e['file']}::{e['class']}" for e in data.get("offsetViolations", [])}

    new_v = [r for r in offset if f"{r['file']}::{r['class']}" not in baseline]
    stale = sorted(baseline - {f"{r['file']}::{r['class']}" for r in offset})

    print("=" * 72)
    print("分页入参单一真源门禁（PageParam / SortablePageParam / CursorPageParam）")
    print(f"  offset 裸 class（阻断域）: {len(offset)}（基线 {len(offset)-len(new_v)} / 新增 {len(new_v)}）")
    print(f"  游标入参未继承 CursorPageParam（🟡 仅报告）: {len(cursor)}")
    print(f"  仅 limit 规模上限（🟢 非分页范式，不判违规）: {len(bounded)}")
    print("=" * 72)

    if stale:
        print(f"\n⚠️baseline 中已无对应代码（应移除，属基线收缩）: {len(stale)} 条")
        for s in stale[:10]:
            print(f"    {s}")

    if cursor:
        print(f"\n🟡游标分页入参 {len(cursor)} 个（应有 cursor 字段并继承 CursorPageParam）:")
        for r in cursor:
            print(f"    {r['file']}  ::  {r['class']}  fields={r['fields']}")
        print("    → 继承 CursorPageParam（cursor/limit + @Min(1)/@Max(100)），勿内联字段。")

    if bounded:
        print(f"\n🟢 带 limit 但无 cursor 的 {len(bounded)} 个：**不是游标分页**，是单次查询规模上限:")
        for r in bounded:
            print(f"    {r['file']}  ::  {r['class']}  fields={r['fields']}")
        print("    → 「导出行数上限」「结果集截断」语义，非分页范式，故不判违规；")
        print("       若确为分页意图，请改用 offset（extends PageParam）或游标（extends CursorPageParam）。")

    if new_v:
        print(f"\n❌ 发现 {len(new_v)} 个【新增】未继承分页真源的 offset 入参类:")
        for r in new_v:
            print(f"    {r['file']}  ::  {r['class']}  fields={r['fields']}")
        print("\n修复方式：让该类 extends PageParam（需要排序则 extends SortablePageParam），")
        print("         删除本地重复的 page/size 字段，校验注解随之继承。")
        print("         确属 cursor 范式 → 先落地 CursorPageParam 再继承它，不要内联 limit。")
        if not args.report_only:
            return 1

    if not new_v:
        print("\n✅ 新增违规为 0（存量基线只可收缩）")
    return 0


if __name__ == "__main__":
    sys.exit(main())