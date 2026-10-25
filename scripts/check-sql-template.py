#!/usr/bin/env python3
"""SQL 模板门禁：三条 SDK 硬约束。

为什么需要这条门禁（2026-10-25 理赔联调实测，三个坑同时炸）
--------------------------------------------------------------
bone-metadata-sdk 的 `.sql` 模板不是"原样喂给 JDBC"，它有两步会改写文本：

1. **语句类型判定**（`TemplateContentParser#detectSqlType`）
       sql.toUpperCase().trim().startsWith("SELECT"|"INSERT"|"UPDATE"|"DELETE"|"WITH")
   纯文本前缀匹配、不剥注释。模板以 `--` 开头 ⇒ 类型判为 `UNKNOWN` ⇒ 执行期抛
   `不支持的 SQL 类型: UNKNOWN`（接口 500，编译期/启动期均不报错）。

2. **租户锚点替换**（`TenantSqlRewriter`）
   `/*bone:tenant*/` 被替换为 `<column> = :__boneTenantId__`，**只替换表达式、不补 `AND`**。
   模板写成 `WHERE x = :x` 换行 `/*bone:tenant*/`，会得到 `WHERE x = :x tenant_id = ?`（语法错）。

3. **空白压平**
   改写后整条 SQL 被压成一行再执行。因此模板里的 `--` 行注释会变成"行内注释"，
   把它之后的所有语句一起注释掉 ⇒ `bad SQL grammar ... near ''`。

三条约束（本门禁强制）
--------------------------------------------------------------
A. 首个非空行必须以语句关键字打头（`SELECT` / `INSERT` / `UPDATE` / `DELETE` / `WITH`）。
B. 全文不得出现 `--` 行注释——说明性文字写进仓储接口方法的 Javadoc（那里才是给人看的）。
C. `/*bone:tenant*/` 锚点前必须是 `AND`（或紧跟 `WHERE`），保证替换后仍是合法布尔表达式。

范围：运行时路径上的 `*/src/main/resources/sql/**`；`sqlFragment/` 片段（列清单、
条件片段）与 `src/test/**` 夹具不在其列。

用法:
  python3 scripts/check-sql-template.py          # 违规则退出码 1
  python3 scripts/check-sql-template.py --json   # 机器可读
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
KEYWORD = re.compile(r"^(SELECT|INSERT|UPDATE|DELETE|WITH)\b", re.I)
ANCHOR = "/*bone:tenant*/"


def templates() -> list[Path]:
    """运行时 SQL 模板：*/src/main/resources/sql/**/*.sql，排除片段目录。"""
    out: list[Path] = []
    for p in REPO.rglob("*.sql"):
        s = p.as_posix()
        if "/target/" in s or "/src/main/resources/" not in s:
            continue
        parts = p.parts
        if "sqlFragment" in parts:
            continue
        try:
            i = parts.index("resources")
        except ValueError:
            continue
        if len(parts) <= i + 1 or parts[i + 1] != "sql":
            continue
        out.append(p)
    return sorted(out)


def check(path: Path) -> list[str]:
    """返回该模板的违规说明（空列表 = 合规）。"""
    text = path.read_text(encoding="utf-8", errors="ignore")
    raw_lines = text.splitlines()
    non_empty = [ln.strip() for ln in raw_lines if ln.strip()]
    problems: list[str] = []

    # A. 首行关键字
    first = non_empty[0] if non_empty else ""
    if not KEYWORD.match(first):
        problems.append(f"首行不是语句关键字（实际: {first[:60]!r}）——SDK 会判为 UNKNOWN")

    # B. 禁止 `--` 行注释
    for idx, ln in enumerate(raw_lines, 1):
        if ln.strip().startswith("--"):
            problems.append(f"第 {idx} 行是 `--` 行注释——SDK 压平后会注释掉后续 SQL")
            break

    # C. 锚点前必须有 AND / WHERE
    for idx, ln in enumerate(raw_lines, 1):
        if ANCHOR not in ln:
            continue
        before = ln.split(ANCHOR)[0].rstrip()
        if before.strip():
            if not re.search(r"\b(AND|WHERE)\s*$", before, re.I):
                problems.append(f"第 {idx} 行锚点前缺 AND（替换后不是合法布尔表达式）")
            continue
        prev = [x.strip() for x in raw_lines[: idx - 1] if x.strip()]
        prev_line = prev[-1] if prev else ""
        if not re.search(r"\b(AND|WHERE)\s*$", prev_line, re.I):
            problems.append(f"第 {idx} 行锚点前缺 AND（上一行未以 AND/WHERE 结尾）")

    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    scanned = 0
    violations = []
    for path in templates():
        scanned += 1
        problems = check(path)
        if problems:
            violations.append({"file": path.relative_to(REPO).as_posix(), "problems": problems})

    if args.json:
        payload = {"scanned": scanned, "violations": violations}
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 1 if violations else 0

    print(f"SQL 模板检查：扫描 {scanned} 个，违规 {len(violations)} 个")
    for v in violations:
        print(f"  - {v['file']}")
        for p in v["problems"]:
            print(f"      · {p}")
    if violations:
        print("修复口径见本脚本 docstring（A 首行关键字 / B 无 -- 注释 / C 锚点前置 AND）。")
        return 1
    print("OK: 首行关键字、无行注释、租户锚点前置 AND 三项均合规")
    return 0


if __name__ == "__main__":
    sys.exit(main())
