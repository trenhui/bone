#!/usr/bin/env python3
"""租户表 ↔ 实体声明一致性检查（ADR-0034 派生门禁）。

规则真源：`doc/architecture/Bone-多租户规范.md` §4「实体声明」。

为什么需要它：SDK 判定"这张表是不是租户表"看的是**实体字段**——
`TableMetadataResolver.parseColumns(entityClass)` 建列元数据，`TableMetadata.isTenantScoped()` 只认
字段名 `tenantId` 或物理列 `tenant_id`/`tenantId`。DDL 有 `tenant_id` 而实体不声明 `tenantId`，
`TenantFilterInjector.inject()` 会**直接 return**：该实体所有 Criteria / QueryBuilder 查询都不注入租户条件，
形成静默跨租户读。这道检查把"DDL 有列但实体不声明"的表全部列出来。

2026-09-20 首次运行实测：12 张表命中（bone-integration 5、bone-system 6、metadata-engine 1），
其中 integration 的例子已造成用户可达的跨租户读（见 `bone-platform/bone-integration/README.md`）。

存量缺口登记在 `doc/architecture/tenant-entity-baseline.json`，只可收缩：
新增表命中即失败；基线内表的分类必须写清（pending = 待补实体声明 + 数据归属迁移；by-design = 确为平台全局表）。

用法：
  python3 scripts/check-tenant-entity-declaration.py            # 报告（不失败）
  python3 scripts/check-tenant-entity-declaration.py --check    # 新增缺口即失败（ci-check.sh 调用）
  python3 scripts/check-tenant-entity-declaration.py --baseline # 把当前缺口刷进基线
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DDL = REPO / "bone-init.sql"
BASELINE = REPO / "doc/architecture/tenant-entity-baseline.json"

# 视为"实体已声明租户"的基类（它们各自声明了 tenantId 字段）。
TENANT_BASES = ("TenantAggregateRoot", "TenantAbstractEntity")
# 实体自己声明 tenantId 字段的形态（SDK 按字段名判定，与访问修饰符无关）。
TENANT_FIELD = re.compile(r"\btenantId\s*[;=]")
TABLE_ANNOTATION = re.compile(r'@Table\(\s*"([^"]+)"')
CREATE_TABLE = re.compile(
    r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?\s*\((.*?)\n\)\s*[^;]*;", re.S | re.I
)
TENANT_COLUMN = re.compile(r"^\s*`?tenant_id`?\s", re.M)
SCAN_ROOTS = ("bone-platform", "bone-engine", "bone-framework", "bone-blueprint", "bone-sdk")
SKIP_DIRS = {"target", "node_modules", ".git", "build", "dist"}


def parse_tenant_tables() -> set[str]:
    sql = DDL.read_text(encoding="utf-8", errors="ignore")
    return {
        m.group(1)
        for m in CREATE_TABLE.finditer(sql)
        if TENANT_COLUMN.search(m.group(2))
    }


def parse_entities() -> dict[str, dict]:
    """返回 {表名: {entity, path, declaresTenant}}。"""
    entities: dict[str, dict] = {}
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.is_dir():
            continue
        for java in base.rglob("*.java"):
            if SKIP_DIRS & set(java.parts) or "/src/main/java/" not in java.as_posix():
                continue
            text = java.read_text(encoding="utf-8", errors="ignore")
            table = TABLE_ANNOTATION.search(text)
            if not table:
                continue
            declares = any(b in text for b in TENANT_BASES) or bool(TENANT_FIELD.search(text))
            entities[table.group(1)] = {
                "entity": java.stem,
                "path": java.relative_to(REPO).as_posix(),
                "declaresTenant": declares,
            }
    return entities


def find_gaps() -> dict[str, dict]:
    entities = parse_entities()
    return {
        table: meta
        for table, meta in sorted(entities.items())
        if table in parse_tenant_tables() and not meta["declaresTenant"]
    }


def load_baseline() -> dict:
    if not BASELINE.exists():
        return {}
    return json.loads(BASELINE.read_text(encoding="utf-8")).get("tables", {})


def write_baseline(gaps: dict[str, dict]) -> None:
    doc = {
        "_comment": (
            "租户表 ↔ 实体声明缺口的存量登记（唯一真源）。规则见 Bone-多租户规范.md §4："
            "SDK 按实体字段判定租户表，DDL 有 tenant_id 但实体不声明 tenantId 等于查询不做租户过滤。"
            "新增命中即失败；本表内表只可收缩。classification：pending（待补实体声明 + 数据归属迁移，"
            "涉及存量数据属 L4）/ by-design（确为平台全局表，须写明理由）。"
        ),
        "_rule": "DDL 含 tenant_id 的表，其 @Table 实体必须声明 tenantId（继承 Tenant* 基类或自带 tenantId 字段）",
        "tables": {
            table: {
                "entity": meta["entity"],
                "path": meta["path"],
                "classification": "pending",
                "note": "待补实体租户声明；迁移存量 tenant_id 归属后按 ADR-0034 清零",
            }
            for table, meta in sorted(gaps.items())
        },
    }
    BASELINE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="出现基线外的新缺口即失败")
    ap.add_argument("--baseline", action="store_true", help="把当前缺口刷进基线")
    args = ap.parse_args()

    if not DDL.exists():
        print("未找到 DDL 文件：{}".format(DDL), file=sys.stderr)
        return 1

    gaps = find_gaps()

    if args.baseline:
        write_baseline(gaps)
        print("OK: 已写入基线 {} 张表 → {}".format(len(gaps), BASELINE.relative_to(REPO)))
        return 0

    if not args.check:
        print("租户表 ↔ 实体声明报告（{} 张含 tenant_id 的表）".format(len(parse_tenant_tables())))
        if not gaps:
            print("OK: 全部租户表的实体都声明了 tenantId")
            return 0
        baseline = load_baseline()
        print("  缺口 {} 张：".format(len(gaps)))
        for table, meta in gaps.items():
            cls = baseline.get(table, {}).get("classification", "NEEDS-CLASSIFICATION")
            print("    {:<24} {:<22} [{}]".format(table, meta["entity"], cls))
        print("  说明：缺口意味着该实体的查询不做租户过滤；分类结论写入 {}".format(BASELINE.relative_to(REPO)))
        return 0

    baseline = load_baseline()
    errors = [
        "租户表 `{}`（实体 {}，{}）未声明租户且不在基线中——DDL 有 tenant_id 而实体不声明，"
        "该实体的查询不会注入租户条件（Bone-多租户规范 §4）".format(
            table, meta["entity"], meta["path"]
        )
        for table, meta in gaps.items()
        if table not in baseline
    ]
    for table in sorted(set(baseline) - set(gaps)):
        print("提示：`{}` 已不在缺口列表中，可从基线移除".format(table))

    if errors:
        print("租户表 ↔ 实体声明检查失败：")
        for e in errors:
            print("  - {}".format(e))
        return 1

    by_kind: dict[str, int] = {}
    for meta in baseline.values():
        kind = meta.get("classification", "needs-owner-decision")
        by_kind[kind] = by_kind.get(kind, 0) + 1
    breakdown = "、".join("{} {} 张".format(k, v) for k, v in sorted(by_kind.items()))
    print(
        "OK: 租户表 ↔ 实体声明检查通过（新增缺口 0；基线内 {} 张存量缺口未扩大：{}）".format(
            len(baseline), breakdown or "无"
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
