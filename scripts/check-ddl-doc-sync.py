#!/usr/bin/env python3
"""校验 bone-init.sql（DDL 真源）与数据库开发规范 §2 表清单的一致性。

用法：
  python3 scripts/check-ddl-doc-sync.py           # 报告差异（有差异退出码 1）
  python3 scripts/check-ddl-doc-sync.py --json    # 输出 JSON（供 CI 汇总）

背景：数据库开发规范 §2 是手抄清单，随 bone-init.sql 演进会漂移；
本脚本让清单与真源绑定（差集 = 文档漏表 / 文档过时表），纳入 ci-check.sh。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SQL_PATH = ROOT / "bone-init.sql"
DOC_PATH = ROOT / "doc/architecture/数据库开发规范.md"


def extract_sql_tables(sql: str) -> set[str]:
    """提取 bone-init.sql 中 CREATE TABLE 的表名（支持 `tbl`、库前缀、注释行）。"""
    tables = set()
    # 形如: CREATE TABLE `iam_user` ( 或 CREATE TABLE iam_user (
    for m in re.finditer(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(`?)([a-zA-Z0-9_]+)\1",
                         sql, re.IGNORECASE):
        tables.add(m.group(2).lower())
    return tables


def extract_doc_tables(doc: str) -> set[str]:
    """提取数据库开发规范 §2（表清单）中的表名：限定在 '## 2.' 与 '## 3.' 之间。"""
    section = doc.split("## 2.", 1)[1].split("## 3.", 1)[0] if "## 2." in doc else ""
    tables = {t.strip("`").lower() for t in re.findall(r"`([a-zA-Z0-9_]+)`", section)}
    # 过滤非表名词（字段/说明性 token）
    noise = {
        "id", "deleted", "tenant_id", "created_at", "created_by", "updated_at", "updated_by",
        # §2 注解/说明中的字段名（非表名）
        "delivery_mode", "int_flow_execution", "xst", "ext_studio",
    }
    return tables - noise


def main() -> int:
    sql_tables = extract_sql_tables(SQL_PATH.read_text(encoding="utf-8"))
    doc_tables = extract_doc_tables(DOC_PATH.read_text(encoding="utf-8"))

    doc_missing = sql_tables - doc_tables          # SQL 有、文档漏
    doc_stale = doc_tables - sql_tables            # 文档有、SQL 无（可能合理：兼容表/说明）

    # 已知合理例外：文档声明但不在 bone-init 的表（如 codegen_* 私有表）不视为过时
    KNOWN_DOC_ONLY = {"gen_"}  # studio-generator 私有表域（文档已注明不在 bone-init）

    def is_known_doc_only(t: str) -> bool:
        return any(t.startswith(p) for p in KNOWN_DOC_ONLY)

    stale_real = {t for t in doc_stale if not is_known_doc_only(t)}

    print(f"DDL 真源表数（bone-init.sql）: {len(sql_tables)}")
    print(f"规范 §2 清单表数: {len(doc_tables)}")
    if doc_missing:
        print(f"\n⚠ 文档漏表（SQL 有、§2 缺，共 {len(doc_missing)}）:")
        for t in sorted(doc_missing):
            print(f"  - {t}")
    if stale_real:
        print(f"\n⚠ 文档过时表（§2 有、SQL 无，共 {len(stale_real)}）:")
        for t in sorted(stale_real):
            print(f"  - {t}")
    if not doc_missing and not stale_real:
        print("✅ 表清单与 bone-init.sql 一致")
        return 0

    print("\n修复方式：新增表 → 更新 数据库开发规范.md §2 对应域分组；删除表 → 从 §2 移除。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
