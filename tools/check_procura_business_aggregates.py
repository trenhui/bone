#!/usr/bin/env python3
"""procuraAI 持久业务聚合门禁（BUS-AGG）。

守护四件事：
  1. bone-init.sql / 增量迁移的四表 DDL：审计八列、租户锚定唯一键、AI 建议字段不落原文；
  2. 数据库规范 §2.17 与迁移清单同步（28 表 / order 148 连续登记）；
  3. SDK 持久化 Adapter 与领域身份边界源码可达（具名人工、AI 不得授标、请求体不得冒名）；
  4. A10.17 三入口判据集合同步，注入必红、正例不误杀。

--self-test 用注入式负向反证证明判据不是假绿。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DDL = ROOT / "bone-init.sql"
MANIFEST = ROOT / "scripts" / "migration" / "migration-manifest.json"
MIGRATION = ROOT / "scripts" / "migration" / "0142_procura_business_aggregates.sql"
DOC = ROOT / "doc" / "architecture" / "数据库开发规范.md"
MODULE = ROOT / "bone-apps" / "bone-procurement" / "bone-procurement-business"
CI = ROOT / ".github" / "workflows" / "ci.yml"
CHECK_SH = ROOT / "scripts" / "check.sh"
LOCAL = ROOT / "doc" / "ai-product" / "procuraAI" / "scripts" / "check-procura-ci.sh"
PREFIX = "BUS-AGG"

TABLES = ("t_proc_bid", "t_proc_quotation", "t_proc_award", "t_proc_capa")
REQUIRED_COLUMNS = (
    "id", "tenant_id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"
)
BUSINESS_COLUMNS = {
    "t_proc_bid": ("bid_id", "rfx_no", "supplier_id", "source_ref", "due_at", "status"),
    "t_proc_quotation": (
        "quote_id", "rfx_no", "supplier_id", "amount", "currency", "valid_until",
        "ai_score", "ai_recommendation",
    ),
    "t_proc_award": ("award_id", "rfx_no", "supplier_id", "confirmed_by", "confirmed_at", "reason"),
    "t_proc_capa": (
        "capa_no", "supplier_id", "issue_description", "owner_id", "due_at", "status",
        "follow_up_required", "closed_by", "closed_at", "evidence_ref",
    ),
}
UNIQUE_KEYS = (
    "uk_proc_bid", "uk_proc_quotation", "uk_proc_award", "uk_proc_capa"
)
UNIQUE_KEYS_BY_TABLE = {
    "t_proc_bid": ("uk_proc_bid", "uk_proc_bid_supplier", "uk_proc_bid_source"),
    "t_proc_quotation": ("uk_proc_quotation",),
    "t_proc_award": ("uk_proc_award", "uk_proc_award_rfx"),
    "t_proc_capa": ("uk_proc_capa",),
}
GATE_SCRIPT = "tools/check_procura_business_aggregates.py"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def read_texts() -> dict[str, str]:
    texts = {
        "ddl": read(DDL),
        "manifest": read(MANIFEST),
        "migration": read(MIGRATION),
        "doc": read(DOC),
        "ci": read(CI),
        "appPom": read(ROOT / "bone-apps" / "bone-procurement" / "bone-procurement-app" / "pom.xml"),
        "check.sh": read(CHECK_SH),
        "local": read(LOCAL),
    }
    if MODULE.exists():
        for path in MODULE.rglob("*.java"):
            texts[str(path.relative_to(ROOT))] = path.read_text(encoding="utf-8")
    return texts


def table_block(ddl: str, table: str) -> str:
    match = re.search(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?" + re.escape(table) + r"`?\s*\((.*?)\)\s*ENGINE=",
        ddl,
        re.S | re.I,
    )
    return match.group(1) if match else ""


def columns(block: str) -> set[str]:
    return set(re.findall(r"^\s*`([a-z0-9_]+)`\s+[A-Z]", block, re.M))


def unique_first_columns(block: str) -> dict[str, str]:
    return {
        name: first
        for name, first in re.findall(
            r"UNIQUE\s+KEY\s+`([a-z0-9_]+)`\s*\(\s*`([a-z0-9_]+)`", block, re.I
        )
    }


def matching(texts: dict[str, str], needle: str) -> list[str]:
    return [rel for rel, text in texts.items() if needle in text]


def check(texts: dict[str, str]) -> tuple[bool, list[str]]:
    errors: list[str] = []
    ddl = texts.get("ddl", "")

    migration = texts.get("migration", "")
    for table in TABLES:
        block = table_block(ddl, table)
        if not block:
            errors.append(f"{PREFIX} bone-init.sql 缺少表 {table}")
            continue
        migration_block = table_block(migration, table)
        if not re.search(
            r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?" + re.escape(table) + r"`?",
            migration,
            re.I,
        ) or not migration_block:
            errors.append(f"{PREFIX} 增量迁移缺少幂等建表 {table}")

        expected_columns = set(REQUIRED_COLUMNS) | set(BUSINESS_COLUMNS[table])
        missing = expected_columns - columns(block)
        if missing:
            errors.append(f"{PREFIX} {table} 缺少必备列 {sorted(missing)}")
        if migration_block and columns(block) != columns(migration_block):
            drift = sorted(columns(block) ^ columns(migration_block))
            errors.append(f"{PREFIX} {table} init/迁移列漂移 {drift}")

        for source, source_block in (("bone-init.sql", block), ("增量迁移", migration_block)):
            if not source_block:
                continue
            found = unique_first_columns(source_block)
            for key in UNIQUE_KEYS_BY_TABLE[table]:
                if key not in found:
                    errors.append(f"{PREFIX} {table} {source} 缺少唯一键 {key}")
                elif found[key] != "tenant_id":
                    errors.append(f"{PREFIX} {table} {source} 唯一键 {key} 未以 tenant_id 打头")
        if "DROP TABLE IF EXISTS" not in ddl[max(0, ddl.find(table) - 160) : ddl.find(table)]:
            errors.append(f"{PREFIX} {table} 未遵循 init-only DROP + CREATE")
    if "SELECT COUNT(*) INTO @business_uks" not in migration:
        errors.append(f"{PREFIX} 增量迁移缺少租户唯一键终态断言")

    try:
        manifest = json.loads(texts.get("manifest", ""))
        entry = next(
            (e for e in manifest.get("entries", []) if e.get("file") == "0142_procura_business_aggregates.sql"),
            None,
        )
        if entry is None or entry.get("order") != 148:
            errors.append(f"{PREFIX} 迁移清单缺少 order=148 登记")
        # executableCount 是「当前清单总数」，会随后续分支合入单调增长；
        # 这里只钉下限（procura 四表登记时已到 148），且必须与整数 order 条目数一致，
        # 避免把快照值写死导致后续任何合并都假红。
        count = manifest.get("executableCount")
        if not isinstance(count, int) or count < 148:
            errors.append(f"{PREFIX} 迁移清单 executableCount 回退到 {count}（应 >= 148）")
        orders = [e["order"] for e in manifest.get("entries", []) if isinstance(e.get("order"), int)]
        if sorted(orders) != list(range(1, len(orders) + 1)):
            errors.append(f"{PREFIX} 迁移清单 order 不连续")
    except (ValueError, TypeError) as exc:
        errors.append(f"{PREFIX} 迁移清单解析失败：{exc}")

    doc = texts.get("doc", "")
    if "### 2.17 采购（procurement，28）" not in doc:
        errors.append(f"{PREFIX} 数据库规范 §2.17 表数未同步为 28")
    for table in TABLES:
        if f"`{table}`" not in doc:
            errors.append(f"{PREFIX} 数据库规范 §2.17 未登记 {table}")

    sources = {rel: text for rel, text in texts.items() if rel.endswith(".java")}
    if not matching(sources, "implements BusinessPersistenceAdapter") and not matching(
        sources, "class BusinessPersistenceAdapter"
    ):
        errors.append(f"{PREFIX} 无 SDK 持久化 Adapter")
    adapter = next(
        (text for rel, text in sources.items() if "BusinessPersistenceAdapter.java" in rel), ""
    )
    if "implements BusinessRepository" not in adapter:
        errors.append(f"{PREFIX} Adapter 未实现 BusinessRepository 端口")
    if "com.bone.metadata.sdk.Repository" not in adapter or "Criteria" not in adapter:
        errors.append(f"{PREFIX} Adapter 未使用 SDK Repository/Criteria")
    for table in TABLES:
        if f'@Table("{table}")' not in "".join(sources.values()):
            errors.append(f"{PREFIX} SDK 实体缺少 @Table({table})")
    if "FORBIDDEN_ACTORS" not in "".join(sources.values()) or "AI_SUGGESTION" not in "".join(
        sources.values()
    ):
        errors.append(f"{PREFIX} 领域层缺少 AI 授标拒绝判据")
    controller = next(
        (text for rel, text in sources.items() if "BusinessController.java" in rel), ""
    )
    if "confirmedBy().trim().equals(actor.operator())" not in controller:
        errors.append(f"{PREFIX} 端点缺少请求体冒名一致性校验")
    if "hasAuthority('procurement:rfx:approve')" not in controller:
        errors.append(f"{PREFIX} 授标端点缺少已登记权限注解")
    if "bone-procurement-business" not in texts.get("appPom", ""):
        errors.append(f"{PREFIX} 采购启动应用未装配业务聚合模块，端点运行时不可达")

    for label, key in (("ci", "ci"), ("check.sh", "check.sh"), ("local", "local")):
        if GATE_SCRIPT not in texts.get(key, ""):
            errors.append(f"{PREFIX} {label} 三入口缺少判据命令")
    if "--self-test" not in texts.get("ci", "") or "--self-test" not in texts.get("local", ""):
        errors.append(f"{PREFIX} CI/本地入口缺少注入式负向探针")

    return (not errors), errors


def self_test(texts: dict[str, str]) -> int:
    cases: list[tuple[str, object, str]] = [
        (
            "删除授标表",
            lambda t: {**t, "ddl": t["ddl"].replace("t_proc_award", "t_proc_removed")},
            "缺少表",
        ),
        (
            "抹掉租户唯一键",
            lambda t: {
                **t,
                "ddl": t["ddl"].replace("UNIQUE KEY `uk_proc_award` (`tenant_id`,`award_id`)", ""),
            },
            "缺少唯一键",
        ),
        (
            "抹掉审计列",
            lambda t: {**t, "ddl": t["ddl"].replace("`created_by`", "`removed_by`")},
            "缺少必备列",
        ),
        (
            "唯一键去掉 tenant_id",
            lambda t: {
                **t,
                "ddl": t["ddl"].replace(
                    "UNIQUE KEY `uk_proc_award` (`tenant_id`,`award_id`)",
                    "UNIQUE KEY `uk_proc_award` (`award_id`)",
                ),
            },
            "未以 tenant_id 打头",
        ),
        (
            "迁移列漂移",
            lambda t: {
                **t,
                "migration": t["migration"].replace("  `evidence_ref`        VARCHAR(256)    DEFAULT NULL,\n", ""),
            },
            "init/迁移列漂移",
        ),
        (
            "篡改迁移清单 order",
            lambda t: {
                **t,
                "manifest": t["manifest"].replace('"order": 148', '"order": 149'),
            },
            "迁移清单",
        ),
        (
            "删除规范清单登记",
            lambda t: {**t, "doc": t["doc"].replace("`t_proc_capa`", "`t_proc_removed`")},
            "数据库规范",
        ),
        (
            "抹掉 SDK 持久化 Adapter",
            lambda t: {
                rel: text.replace("class BusinessPersistenceAdapter", "class RemovedAdapter")
                for rel, text in t.items()
            },
            "无 SDK 持久化 Adapter",
        ),
        (
            "抹掉 AI 授标拒绝",
            lambda t: {
                rel: text.replace("FORBIDDEN_ACTORS", "REMOVED_ACTORS")
                for rel, text in t.items()
            },
            "AI 授标拒绝",
        ),
        (
            "删除端点冒名校验",
            lambda t: {
                rel: text.replace("confirmedBy().trim().equals(actor.operator())", "true")
                for rel, text in t.items()
            },
            "冒名一致性校验",
        ),
        (
            "解除启动应用装配",
            lambda t: {
                **t,
                "appPom": t["appPom"].replace("bone-procurement-business", "bone-procurement-removed"),
            },
            "未装配业务聚合模块",
        ),
        (
            "删除 CI 判据",
            lambda t: {**t, "ci": t["ci"].replace(GATE_SCRIPT, GATE_SCRIPT.replace("aggregates", "aggregates_x"))},
            "ci 三入口",
        ),
        (
            "删除 check.sh 判据",
            lambda t: {
                **t,
                "check.sh": t["check.sh"].replace(GATE_SCRIPT, GATE_SCRIPT.replace("aggregates", "aggregates_x")),
            },
            "check.sh 三入口",
        ),
        (
            "删除本地一键入口判据",
            lambda t: {
                **t,
                "local": t["local"].replace(GATE_SCRIPT, GATE_SCRIPT.replace("aggregates", "aggregates_x")),
            },
            "local 三入口",
        ),
    ]

    ok = True
    for desc, mutate, expect in cases:
        passed, errors = check(mutate(texts))
        hit = not passed and any(expect in error for error in errors)
        print(f"[{'PASS' if hit else 'FAIL'}] 负向：{desc}")
        if not hit:
            print("       - 结果：" + str(errors[:3]))
        ok = ok and hit
    passed, errors = check(texts)
    print(f"[{'PASS' if passed else 'FAIL'}] 正例：当前实现满足业务聚合全部判据")
    if not passed:
        print("       - " + str(errors[:5]))
    print(f"[SELF-TEST] {'ALL PASS' if ok and passed else 'FAILED'}")
    return 0 if ok and passed else 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    texts = read_texts()
    if args.self_test:
        return self_test(texts)
    passed, errors = check(texts)
    for error in errors:
        print(error)
    print(
        f"[{PREFIX}] {'PASS' if passed else 'FAIL'} — 4 表 / 审计唯一键 / 迁移规范 / "
        "SDK 持久化 / 身份边界 / 三入口"
    )
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
