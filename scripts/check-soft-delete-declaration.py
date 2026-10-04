#!/usr/bin/env python3
"""软删声明校验：聚合是否具备软删声明（@Deleted），缺失则 deleteById 会物理删行。

设计依据: doc/architecture/soft-delete-declaration-baseline.json（2026-10-03 实测发现）。

为什么需要这个门禁（背景）:
    bone-metadata-sdk 判定聚合可否软删的依据是**实体内是否存在带 @Deleted 注解的字段**
    （TableMetadataResolver → ColumnMetadata.isSoftDeleted → TableMetadata#isSoftDeletable）。
    BaseRepository#deleteById 据此二选一：
      - 命中 → "UPDATE t SET deleted = true WHERE id = :p0"（软删）
      - 未命中 → "DELETE FROM t WHERE id = :p0"（**物理删除，行永久消失**）
    这与 DDL 有没有 deleted 列**无关** —— 表有列、实体没声明，照样物理删。

    @Deleted 只声明在 bone-core 的 AbstractEntity（→ TenantAbstractEntity）；
    而 AggregateRoot / TenantAggregateRoot **不提供** deleted 字段（其 javadoc 明示
    "审计由子类自行声明"）。于是"extends 聚合根+ 表带 deleted 列"的实体一律物理删，
    且调用方拿到 HTTP 200 毫不知情。

    2026-10-03 实测（开发库，勿凭推断改动）:
      - mdm_steward：DELETE /masterdata/governance-roles/{id} → 行数 1→0
      - sys_config：造探针后 DELETE /system/config/{id}      → 行数 1→0
      - 反证 mdm_field（extends TenantAbstractEntity，基类自带 @Deleted）→ 软删正常

用法:
    python3 scripts/check-soft-delete-declaration.py# 阻断模式（有新增缺口 → exit 1）
    python3 scripts/check-soft-delete-declaration.py --report-only  # 只出报告，恒 exit 0

豁免: 实体标注 @PhysicalDelete(reason="...") 表示「本表本就该物理删」已显式声明，
     不再计入缺口（2026-10-03 起）。理由必填，且不得同时声明 @Deleted 字段（二者语义相反）。

存量语义（与仓库既有基线一致）:
    存量缺口登记在 doc/architecture/soft-delete-declaration-baseline.json，语义**只可收缩**：
    基线内的表不阻断（存量已登记），**基线外新出现的缺口才阻断**。
    某表补齐 @Deleted（恢复软删）后可将其从基线移除。
"""

import argparse
import json
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASELINE = "doc/architecture/soft-delete-declaration-baseline.json"
INIT_SQL = "bone-init.sql"
JAVA_ROOTS = ["bone-platform", "bone-engine", "bone-framework", "bone-tool"]

# 这些基类（及其子类）自带 @Deleted deleted 字段 ⇒ deleteById 走软删
SOFT_DELETE_BASES = {"AbstractEntity", "TenantAbstractEntity", "SoftDeletable"}

# @PhysicalDelete(reason = "...") ⇒ 显式声明「本表本就该物理删」，合规豁免。
# 要求 reason 非空：业界实践要求删除策略必须自带理由，避免「物理删」变成无需解释的默认。
# 字段级 @Deleted 声明：@Deleted 后可跟其它注解与修饰符，最终以标识符 + 类型结尾。
# 排除 import（`@Deleted;` / `@Deleted.`）与 javadoc 里的 {@link ...Deleted} 引用。
# 字段级 @Deleted 声明。判据要点（每条都对应一种真实的「门禁被绕过」姿势）：
#   1. 必须行首（只允许缩进）——否则 import 语句与 javadoc 里的
#      {@link com.bone.core.annotation.Deleted} 会被误判为已声明；
#   2. 允许注解与字段声明换行 —— spotless(google-java-format) 会把
#      `@Deleted private Boolean deleted = false;` 重排成注解单独一行；
#   3. 必须同行出现布尔/整型类型 —— 排除 javadoc 散文里偶然出现的 "Boolean" 字样。
DELETED_TYPE = r"(?:Boolean|boolean|Integer|int|Long|long)"
DELETED_FIELD_RE = re.compile(
    r"^[ \t]*@Deleted\b[^;\n]*\b" + DELETED_TYPE + r"\b[^;\n]*;"
    r"|^[ \t]*@Deleted\b[ \t]*\r?\n[ \t]*\bprivate\s+"
    + DELETED_TYPE + r"\b[^;\n]*;"
    r"|^[ \t]*@com\.bone\.core\.annotation\.Deleted\b[^;\n]*\b"
    + DELETED_TYPE + r"\b[^;\n]*;"
    r"|^[ \t]*@com\.bone\.core\.annotation\.Deleted\b[ \t]*\r?\n[ \t]*\bprivate\s+"
    + DELETED_TYPE + r"\b[^;\n]*;",
    re.M,
)

PHYSICAL_DELETE_RE = re.compile(
    r'@PhysicalDelete\s*\(\s*reason\s*=\s*"[^"]+"', re.S
)

# @PhysicalDelete 与 @Deleted 字段同存 ⇒ 语义相反（一个声明物理删、一个让 SDK 执行软删），必须阻断
BOTH_DECLARED_RE = re.compile(r'@PhysicalDelete\b[\s\S]{0,2000}?@Deleted\b|@Deleted\b[\s\S]{0,2000}?@PhysicalDelete\b')

SKIP_DIRS = {"target", "node_modules", "dist", "build", ".git", "generated-code", "test"}


def ddl_has_column(ddl: str, table: str, column: str) -> bool:
    """bone-init.sql 里某表是否声明了指定列。"""
    m = re.search(
        r"CREATE\s+TABLE(?: IF NOT EXISTS)?\s+" + re.escape(table) + r"\s*\((.*?)\n\)\s*ENGINE",
        ddl,
        re.S | re.I,
    )
    if not m:
        return False
    return bool(re.search(r"(?m)^\s*`?" + re.escape(column) + r"`?\s", m.group(1), re.I))


def collect_entities(ddl: str):
    """扫描 @Table 实体，返回 [(表名, 实体名, 基类简名, 路径)]。"""
    found = {}
    for root in JAVA_ROOTS:
        base = os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for fn in filenames:
                if not fn.endswith(".java"):
                    continue
                path = os.path.join(dirpath, fn)
                try:
                    src = open(path, encoding="utf-8", errors="ignore").read()
                except OSError:
                    continue
                m = re.search(
                    r'@Table\("([^"]+)"\)[\s\S]{0,400}?class\s+(\w+)\s+extends\s+([\w.]+)',
                    src,
                )
                if not m:
                    continue
                table, cls, parent = m.group(1), m.group(2), m.group(3).split(".")[-1]
                if table in found:
                    continue
                # 实体自身显式声明 @Deleted 字段也算。
                # 判据必须是「字段/方法上的注解」而非全文出现：import 语句
                # `import com.bone.core.annotation.Deleted;` 也含 "@Deleted" 字样，
                # 用 `in` 判会让「删掉字段但忘了删 import」的文件被误判为合规 ⇒ 门禁静默失效。
                if DELETED_FIELD_RE.search(src):
                    continue
                # 显式声明「本就该物理删」→ 合规豁免，不再计入缺口
                if PHYSICAL_DELETE_RE.search(src):
                    continue
                found[table] = (cls, parent, os.path.relpath(path, REPO))
    return found


def collect_physical_delete_entities():
    """扫描显式声明 @PhysicalDelete 的实体，返回 {表名: 路径}。"""
    found = {}
    for root in JAVA_ROOTS:
        base = os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for fn in filenames:
                if not fn.endswith(".java"):
                    continue
                path = os.path.join(dirpath, fn)
                try:
                    src = open(path, encoding="utf-8", errors="ignore").read()
                except OSError:
                    continue
                if not PHYSICAL_DELETE_RE.search(src):
                    continue
                m = re.search(r'@Table\("([^"]+)"\)', src)
                if m:
                    found[m.group(1)] = os.path.relpath(path, REPO)
    return found


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    baseline_path = os.path.join(REPO, BASELINE)
    registered = set()
    if os.path.isfile(baseline_path):
        with open(baseline_path, encoding="utf-8") as fh:
            registered = set(json.load(fh).get("tables", {}).keys())

    ddl = open(os.path.join(REPO, INIT_SQL), encoding="utf-8").read()
    entities = collect_entities(ddl)
    physical = collect_physical_delete_entities()

    gaps = []
    for table, (cls, parent, rel) in sorted(entities.items()):
        if not ddl_has_column(ddl, table, "deleted"):
            continue  # DDL 也没 deleted 列 ⇒ 本就不该软删，不算缺口
        if parent in SOFT_DELETE_BASES:
            continue  # 基类自带 @Deleted ⇒ 软删正常
        gaps.append((table, cls, parent, rel))

    new_gaps = [g for g in gaps if g[0] not in registered]
    stale = sorted(registered - {g[0] for g in gaps})

    # @PhysicalDelete 与 @Deleted 字段同存 ⇒ 语义相反，必须阻断
    conflicts = []
    for table, rel in sorted(physical.items()):
        try:
            src = open(os.path.join(REPO, rel), encoding="utf-8", errors="ignore").read()
        except OSError:
            continue
        if BOTH_DECLARED_RE.search(src):
            conflicts.append((table, rel))

    print(f"显式声明 @PhysicalDelete 的实体：{len(physical)} 张（豁免，不再计入缺口）")
    for table, rel in sorted(physical.items()):
        print(f"  [物理删·已声明] {table:<28}{rel}")
    if conflicts:
        print()
        print("🔴 @PhysicalDelete 与 @Deleted 字段同存（语义相反：前者声明物理删、后者让SDK 执行软删）：")
        for table, rel in conflicts:
            print(f"  - {table}: {rel}")

    print(
        f"扫描 @Table 实体 {len(entities)} 个，"
        f"其中 DDL 带 deleted 列且基类不带 @Deleted 的 {len(gaps)} 张进入缺口判定"
    )
    print(f"软删声明缺口：{len(gaps)} 张（基线已登记 {len(gaps) - len(new_gaps)}，新增 {len(new_gaps)}）")
    for table, cls, parent, rel in gaps:
        mark = "已登记" if table in registered else "🔴 新增缺口"
        print(f"  [{mark}] {table:<28}{cls:<30}extends {parent}")
    if new_gaps:
        print()
        print("新增缺口明细（请补实体 @Deleted 声明以恢复软删，或登记进基线并说明理由）：")
        for table, cls, parent, rel in new_gaps:
            print(f"  - {table}: {rel}")
    if stale:
        print()
        print("⚠️基线中已不再构成缺口的条目（实体已补声明或表已改），可从基线移除：")
        for t in stale:
            print(f"  - {t}")

    if args.report_only:
        return 0
    if conflicts or new_gaps or stale:
        print()
        print("HC-008 补充门禁失败：删除语义必须显式且自洽（软删声明缺口只可收缩）。")
        return 1
    print()
    print("OK: 软删声明校验通过（无新增缺口，基线条目均仍有效）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
