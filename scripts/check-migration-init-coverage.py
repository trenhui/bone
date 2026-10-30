#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-migration-init-coverage.py  (G39 · 全量基线覆盖增量建表)

判据：迁移脚本里 `CREATE TABLE` 建过的每一张表，都必须在 `bone-init.sql` 里存在
      （除已在 `init-coverage-baseline.json` 登记的存量缺口）。

为什么需要这一步
----------------
本仓有**两个 DDL 真源**：
  · `bone-init.sql`：当前全量结构，新环境只灌它；
  · `scripts/migration/*.sql`：存量环境的增量。

两者必须指向同一个结构，否则「新环境」与「已迁移环境」就不等价——而这件事**没有任何
现有门禁在看**：`check-ddl-doc-sync.py` 比的是「表清单文档 ↔ init」，`check-ddl-required-columns.py`
比的是「必备列」，都不看迁移脚本建了什么。

2026-10-30 实测（把 bone-init.sql 灌进空库逐表比对）：
    init = 121 张表；本地 dev 库 = 183 张；差值中 25 张是迁移脚本建的结构表。
    取 6 张关键表双证：wf_task / wf_instance / wf_status_outbox / t_claim_case_track /
    t_claim_inspection / meta_runtime_record_audit —— **init 命中 0、dev 命中 6**。
即：新环境灌完 init，工作流引擎与理赔轨迹/稽核直接缺表。这类缺口不会报错，而是
「代码已上线、DDL 没跟上」（0072 头部记的正是同一种事故），所以必须由门禁而不是人来记。

基线只可收缩（双向）
--------------------
存量 31 项缺口已登记在 `init-coverage-baseline.json`（23 项待并入 / 2 项待裁决 / 6 项疑从未
执行成功）。基线允许存在，是因为一次性把 25 张表并入全量属于 L3 结构变更，要单独评审；
但它**只可收缩**：某张表并入 init 后必须从基线删除，否则判红（否则基线会变成永久白名单，
把「已修好」和「从未修」混为一谈）。新增迁移建表而未并入 init 时**直接判红，不得进基线**。

用法：
  python3 scripts/check-migration-init-coverage.py
  python3 scripts/check-migration-init-coverage.py --init <sql> --manifest <json> \
      --baseline <json> --migration-dir <dir>     # 后四项供变异测试
"""
import argparse
import json
import os
import re
import sys

INIT_REL = "bone-init.sql"
MIGRATION_REL = os.path.join("scripts", "migration")
MANIFEST_REL = os.path.join(MIGRATION_REL, "migration-manifest.json")
BASELINE_REL = os.path.join(MIGRATION_REL, "init-coverage-baseline.json")

CREATE_RX = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?", re.I)
# 脚本内部的备份表（bak_xxx / tmp_xxx）不是结构，是执行前的临时拷贝，不该进全量基线。
BACKUP_PREFIX = ("bak_", "tmp_")


def find_repo_root():
    out = os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
    return out or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def strip_sql_noise(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"--[^\n]*", " ", text)
    text = re.sub(r"'[^'\n]*'", "''", text)
    text = re.sub(r'"[^"\n]*"', '""', text)
    return text


def tables_of(path):
    return {m.group(1).lower() for m in CREATE_RX.finditer(strip_sql_noise(open(path, encoding="utf-8", errors="ignore").read()))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root")
    ap.add_argument("--init")
    ap.add_argument("--migration-dir")
    ap.add_argument("--manifest")
    ap.add_argument("--baseline")
    args = ap.parse_args()

    root = args.root or find_repo_root()
    init_path = args.init or os.path.join(root, INIT_REL)
    mig_dir = args.migration_dir or os.path.join(root, MIGRATION_REL)
    manifest_path = args.manifest or os.path.join(root, MANIFEST_REL)
    baseline_path = args.baseline or os.path.join(root, BASELINE_REL)

    for p, what in ((init_path, "全量基线"), (manifest_path, "迁移清单"), (baseline_path, "覆盖率基线")):
        if not os.path.exists(p):
            print(f"❌ 找不到{what}：{p}（本门禁不接受缺省放行——没有基线就没有可比对的另一半）")
            return 1

    init_tables = tables_of(init_path)
    doc = json.loads(open(manifest_path, encoding="utf-8").read())
    executable = [e for e in doc.get("entries", []) if e.get("kind") in ("ddl", "mixed")]
    if not init_tables or not executable:
        print(f"❌ 自检失败：init 解析出 {len(init_tables)} 张表 / 可建表的迁移 {len(executable)} 个——"
              "任一侧为空都无法判定（防空集合恒过）")
        return 1

    created = {}
    for e in executable:
        path = os.path.join(mig_dir, e["file"])
        if not os.path.exists(path):
            print(f"❌ 清单登记的 `{e['file']}` 不存在，无法解析其建表")
            return 1
        for t in tables_of(path):
            if t.startswith(BACKUP_PREFIX):
                continue
            created.setdefault(t, e["file"])

    baseline_doc = json.loads(open(baseline_path, encoding="utf-8").read())
    baseline = {}
    errors = []
    for r in baseline_doc.get("tables") or []:
        t = str(r.get("table") or "").strip().lower()
        if not t:
            errors.append("基线条目缺 table")
            continue
        if t in baseline:
            errors.append(f"基线里 `{t}` 重复登记")
        if len(str(r.get("reason") or "").strip()) < 8:
            errors.append(f"基线 `{t}` 缺理由（≥8 字：为什么它可以暂时不并入全量）")
        baseline[t] = r

    gap = {t: src for t, src in created.items() if t not in init_tables}

    for t in sorted(set(gap) - set(baseline)):
        errors.append(
            f"`{t}` 由 {gap[t]} 建表，但 bone-init.sql 里没有它 ⇒ 新环境灌 init 后缺此表，"
            "与已迁移环境不等价（新迁移必须同时把结构并入全量；确实暂不并入须登记基线并写理由）"
        )
    for t in sorted(set(baseline) - set(gap)):
        errors.append(
            f"基线里的 `{t}` 已不需要豁免（init 已包含，或已无迁移建它）⇒ **基线只可收缩**，请删除该条"
        )

    if errors:
        print(f"❌ 全量基线覆盖率校验失败：{len(errors)} 项\n")
        for x in errors:
            print("  · " + x)
        print("\n全量基线：" + init_path + "\n覆盖率基线：" + baseline_path)
        return 1

    print(
        "OK: 全量基线覆盖增量建表（init {} 张表 / 迁移建表 {} 张 / 已覆盖 {} / 基线内待并入 {}）".format(
            len(init_tables), len(created), len(set(created) - set(gap)), len(baseline))
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
