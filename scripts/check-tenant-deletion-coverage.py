#!/usr/bin/env python3
"""租户离场清除覆盖门禁（R8①）。

校验 ``bone-init.sql`` 中**含 tenant_id 的表** 是否都被
``TenantDeletionGatewayAdapter.TENANT_TABLES`` 清退清单覆盖。

规则：
- 含 tenant_id 的 DDL 表，减去「平台/全局/样板/中继/待核实」排除集后，
  必须出现在清退清单里；否则视为缺口（新增租户作用域表忘记登记离场清除），
  退出码 1，阻断提交。
- 排除集与 ``TenantDeletionGatewayAdapter`` 注释中的排除口径一致：
  平台/全局表（iam_tenant/iam_permission、mdm_* 平台模板层、meta_model_template*）、
  跨租户中继（int_outbox）、blueprint 样板（t_order/bp_*）、待核实/零引用（cnsl_*/gen_type_mapping）。
  这些表 ``WHERE tenant_id = :tenantId`` 自然不会碰（平台 0 值行），无需清退。
- 清退清单里出现、但 DDL 无 tenant_id 的表（如迁移脚本创建的表）只告警不阻断。

用法：
    python3 scripts/check-tenant-deletion-coverage.py            # 报告模式
    python3 scripts/check-tenant-deletion-coverage.py --check   # CI 门禁，缺口即非零退出
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

DDL_PATH = REPO / "bone-init.sql"
ADAPTER_PATH = (
    REPO
    / "bone-platform"
    / "bone-iam"
    / "src"
    / "main"
    / "java"
    / "com"
    / "bone"
    / "iam"
    / "infrastructure"
    / "gateway"
    / "TenantDeletionGatewayAdapter.java"
)

# 排除集：平台/全局/样板/中继/待核实表（与 TenantDeletionGatewayAdapter 注释一致）。
EXCLUDED_EXACT = {
    "iam_tenant",
    "iam_permission",
    "mdm_domain_template",
    "mdm_reference_set",
    "mdm_reference_value",
    "mdm_template_version",
    "meta_model_template",
    "meta_model_template_field",
    "int_outbox",
    "gen_type_mapping",
    "t_order",
}
# 前缀排除
EXCLUDED_PREFIXES = ("bp_", "cnsl_")


def parse_ddl_tenant_tables() -> set[str]:
    text = DDL_PATH.read_text(encoding="utf-8", errors="ignore")
    blocks = re.findall(
        r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?\s*\((.*?)\n\)\s*[^;]*;",
        text,
        re.S | re.I,
    )
    tables: set[str] = set()
    for name, body in blocks:
        cols = [
            c.strip()
            for c in body.split("\n")
            if c.strip() and not c.strip().startswith("--")
        ]
        if any(re.match(r"`?tenant_id`?", c) for c in cols):
            tables.add(name)
    return tables


def parse_purge_list() -> set[str]:
    text = ADAPTER_PATH.read_text(encoding="utf-8", errors="ignore")
    # 取 TENANT_TABLES = List.of( ... ) 内的字符串字面量
    m = re.search(r"TENANT_TABLES\s*=\s*List\.of\((.*?)\)\s*;", text, re.S)
    if not m:
        # 兼容其他写法
        m = re.search(r"List\.of\((.*?)\)", text, re.S)
    if not m:
        raise SystemExit("无法在 TenantDeletionGatewayAdapter 解析 TENANT_TABLES 清单")
    return set(re.findall(r'"([^"]+)"', m.group(1)))


def is_excluded(table: str) -> bool:
    return table in EXCLUDED_EXACT or any(
        table.startswith(p) for p in EXCLUDED_PREFIXES
    )


def main() -> int:
    ddl_tables = parse_ddl_tenant_tables()
    purge = parse_purge_list()

    in_scope = {t for t in ddl_tables if not is_excluded(t)}
    gaps = sorted(in_scope - purge)
    stale = sorted(purge - ddl_tables)  # 清单里有但 DDL 无 tenant_id（迁移表等）

    print("租户离场清除覆盖门禁（R8①）")
    print("  DDL 含 tenant_id 表：{} 张".format(len(ddl_tables)))
    print("  清退清单表：{} 张".format(len(purge)))
    print("  排除（平台/全局/样板/中继/待核实）：{} 张".format(len(ddl_tables) - len(in_scope)))
    print("  应覆盖（入域）：{} 张".format(len(in_scope)))

    if gaps:
        print("\n  ❌ 缺口 {} 张（含 tenant_id 但未登记离场清退，租户离场会残留数据）：".format(len(gaps)))
        for t in gaps:
            print("    - {}".format(t))
        print("\n  处置：在 TenantDeletionGatewayAdapter.TENANT_TABLES 补登这些表；")
        print("  若确属平台/全局/样板表，须同步加入本脚本 EXCLUDED 集并在 adapter 注释说明。")
    else:
        print("\n  ✅ 全部入域租户表均已登记离场清退，无缺口。")

    if stale:
        print("\n  ⚠ 清退清单中存在 DDL 无 tenant_id 的表（迁移脚本创建或已改名），仅告警：")
        for t in stale:
            print("    - {}".format(t))

    return 1 if gaps else 0


if __name__ == "__main__":
    sys.exit(main())
