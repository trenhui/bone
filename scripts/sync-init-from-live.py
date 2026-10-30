#!/usr/bin/env python3
"""把「已迁移到位」的结构表并入 bone-init.sql 的托管区块，并提供 `--check` 一致性判据。

为什么必须脚本化（不能手工粘贴）
--------------------------------
`bone-init.sql` 是**当前全量结构**的唯一入口，`scripts/migration/` 是**存量环境**的增量集。
两者描述同一套结构却各自维护 ⇒ 必然漂移。2026-10-30 实测：init 建出 121 张表 / 本地 dev 库
183 张，差值里 25 张是迁移脚本建的结构表；取 6 张关键表双证（wf_task / wf_instance /
wf_status_outbox / t_claim_case_track / t_claim_inspection / meta_runtime_record_audit）
**init 命中 0、dev 命中 6**。新环境灌完 init，工作流引擎与理赔轨迹直接缺表，且缺表不报错。

手工把 DDL 抄进 init 只能把漂移周期拉长一轮：抄的那一刻就已经对不上了，且没人能验证抄得
对不对。所以本脚本承担两件事：

  1. 从**活库** `SHOW CREATE TABLE` 导出 → 写入 bone-init.sql 的托管区块；
  2. `--check` 重新导出并逐字节比对区块。活库不可用时打印 SKIP 理由并退出 0
     （与 check-orphan-tables.py 同口径：门禁守护「有库可查时不许漂移」，不是
     「每台机器都必须有库」；跳过必须**可见**——静默跳过比跑错更危险）。

内容规范化只有一处：剥离 `AUTO_INCREMENT=<n>`。那是**数据状态**不是结构，一旦带进基线，
每次重建库都会让 diff 抖动，真正的结构变更反而淹没在噪音里。

托管区块
--------
    -- >>> BEGIN managed: migration-merged
    ...
    -- <<< END managed: migration-merged
区块内一律由本脚本生成，**禁止手工编辑**（改了下次 `--check` 即红）。

MERGED_SOURCES
--------------
「哪些表已并入、由哪个迁移脚本建」的归档记录，**只增不改**：表并入后即固化在此，
不再依赖 init-coverage-baseline.json（那份清单是「待并入缺口」，会随补齐而收缩到空）。
新增并入：把表加进 MERGED_SOURCES → 跑一次本脚本。

用法
----
  python3 scripts/sync-init-from-live.py            # 重写托管区块
  python3 scripts/sync-init-from-live.py --check    # 只比对（CI/pre-commit 用）
  python3 scripts/sync-init-from-live.py --dry-run  # 打印将写入的内容

环境变量：BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER / BONE_DB_PASSWORD / BONE_DB_NAME
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
INIT = REPO / "bone-init.sql"
BEGIN = "-- >>> BEGIN managed: migration-merged"
END = "-- <<< END managed: migration-merged"

# 表名 → 建立它的迁移脚本。**只增不改**（这是归档，不是待办）。
MERGED_SOURCES: dict[str, str] = {
    # 0013 是 bone-metadata-sdk 预留列机制的真源表（ColumnAllocator + column_allocation）
    "field_metadata": "0013_blueprint_reserved_columns.sql",
    "column_allocation": "0013_blueprint_reserved_columns.sql",
    # 0032/0033/0040/0046：元数据平台运行时动作、数据安全审计、模板状态机、UI schema
    "meta_entity_action_def": "0032_runtime_action_spi.sql",
    "meta_runtime_action_execution": "0032_runtime_action_spi.sql",
    "meta_runtime_record_audit": "0033_runtime_data_safety.sql",
    "meta_model_template_action": "0040_template_action_state_machine.sql",
    "meta_entity_ui_schema": "0046_entity_ui_schema.sql",
    "meta_model_template_ui_history": "0046_entity_ui_schema.sql",
    # 0037：报销（L1 关系型）
    "t_expense_category": "0037_runtime_expense_l1_relations.sql",
    "t_expense_reimbursement": "0037_runtime_expense_l1_relations.sql",
    "t_expense_reimbursement_item": "0037_runtime_expense_l1_relations.sql",
    # 0056/0065：工作流引擎
    "wf_process_def": "0056_wf_workflow_tables.sql",
    "wf_instance": "0056_wf_workflow_tables.sql",
    "wf_active_instance": "0056_wf_workflow_tables.sql",
    "wf_task": "0056_wf_workflow_tables.sql",
    "wf_binding": "0056_wf_workflow_tables.sql",
    "wf_notification": "0056_wf_workflow_tables.sql",
    "wf_status_outbox": "0056_wf_workflow_tables.sql",
    "wf_process_draft": "0065_wf_process_draft.sql",
    # 0072：理赔缺失表（轨迹 / 抽检 / SLA / 域事件 outbox / 对账）
    "t_claim_case_track": "0072_synap_claim_missing_tables.sql",
    "t_claim_inspection": "0072_synap_claim_missing_tables.sql",
    "t_claim_case_sla": "0072_synap_claim_missing_tables.sql",
    "t_domain_event_outbox": "0072_synap_claim_missing_tables.sql",
    "t_reconciliation_report": "0072_synap_claim_missing_tables.sql",
    "t_reconciliation_diff": "0072_synap_claim_missing_tables.sql",
}


def _env(name: str, default: str) -> str:
    return os.environ.get(name, default)


def mysql_cli() -> str | None:
    """定位 mysql 客户端。显式 $MYSQL_CLIENT > PATH > 常见安装路径。"""
    explicit = os.environ.get("MYSQL_CLIENT")
    if explicit and Path(explicit).exists():
        return explicit
    found = shutil.which("mysql")
    if found:
        return found
    for cand in ("/usr/local/mysql/bin/mysql", "/opt/homebrew/bin/mysql", "/usr/bin/mysql"):
        if Path(cand).exists():
            return cand
    return None


class Live:
    """活库读取。优先 pymysql（无则退化到 mysql CLI）。不可用时抛 Unavailable。"""

    def __init__(self) -> None:
        self.db = _env("BONE_DB_NAME", "bone")
        self.host = _env("BONE_DB_HOST", "127.0.0.1")
        self.port = _env("BONE_DB_PORT", "3306")
        self.user = _env("BONE_DB_USER", "root")
        self.password = _env("BONE_DB_PASSWORD", "mysql123")
        self._driver = None
        try:
            import pymysql  # noqa: F401

            self._driver = "pymysql"
        except Exception:
            if mysql_cli():
                self._driver = "cli"
            else:
                raise Unavailable("既无 pymysql 也无 mysql 客户端")

    def rows(self, sql: str) -> list[list[str]]:
        if self._driver == "pymysql":
            import pymysql

            conn = pymysql.connect(
                host=self.host, port=int(self.port), user=self.user,
                password=self.password, database=self.db, charset="utf8mb4",
            )
            try:
                with conn.cursor() as cur:
                    cur.execute(sql)
                    return [list(r) for r in cur.fetchall()]
            finally:
                conn.close()
        cli = mysql_cli()
        assert cli
        out = subprocess.run(
            [cli, f"-h{self.host}", f"-P{self.port}", f"-u{self.user}",
             f"-p{self.password}", "-N", "-B", self.db, "-e", sql],
            capture_output=True, text=True,
        )
        if out.returncode != 0:
            raise Unavailable(out.stderr.strip().splitlines()[-1] if out.stderr.strip() else "查询失败")
        return [line.split("\t") for line in out.stdout.splitlines() if line]

    def existing(self) -> set[str]:
        return {
            r[0].lower()
            for r in self.rows(
                "SELECT table_name FROM information_schema.tables "
                f"WHERE table_schema='{self.db}' AND table_type='BASE TABLE'"
            )
        }

    def show_create(self, table: str) -> str:
        rows = self.rows(f"SHOW CREATE TABLE `{table}`")
        if not rows or len(rows[0]) < 2:
            raise Unavailable(f"SHOW CREATE TABLE {table} 无输出")
        return rows[0][1]


class Unavailable(RuntimeError):
    """活库不可用/不可达——降级为显式 SKIP，不静默通过。"""


def normalize(ddl: str) -> str:
    """只剥离 AUTO_INCREMENT=<n>（数据状态，不是结构），其余保持服务端原文。"""
    ddl = re.sub(r"\s+AUTO_INCREMENT=\d+", "", ddl)
    return ddl.rstrip()


def build_block(live: Live) -> str:
    present = live.existing()
    missing = sorted(t for t in MERGED_SOURCES if t not in present)
    if missing:
        raise Unavailable(
            "以下已并入表在活库不存在，无法导出：" + ", ".join(missing)
            + "（若表已废弃，请从 MERGED_SOURCES 移出并说明依据）"
        )

    lines = [
        BEGIN,
        "-- 本区块由 scripts/sync-init-from-live.py 从活库导出，禁止手工编辑（改了 --check 即红）。",
        "-- 内容 = 迁移脚本建过、且已在活库落地的结构表；新环境只需灌本文件。",
        "-- 唯一规范化：剥离 AUTO_INCREMENT=<n>（数据状态，非结构）。",
        "",
    ]
    for table in sorted(MERGED_SOURCES):
        lines.append(f"-- ── {table}（建自 {MERGED_SOURCES[table]}） ──")
        lines.append(f"DROP TABLE IF EXISTS `{table}`;")
        lines.append(normalize(live.show_create(table)) + ";")
        lines.append("")
    lines.append(END)
    return "\n".join(lines)


def splice(text: str, block: str) -> str:
    """把区块写进 init。已有则原地替换，没有则追加到末尾。"""
    if BEGIN in text:
        head, rest = text.split(BEGIN, 1)
        if END not in rest:
            raise SystemExit(f"❌ bone-init.sql 有 {BEGIN} 但缺 {END}，定界符已损坏，请先修复")
        tail = rest.split(END, 1)[1]
        return head + block + tail
    sep = "" if text.endswith("\n") else "\n"
    return text + sep + "\n-- ============================================================\n" \
        "-- 6. 迁移并入的结构（机器生成，勿手工编辑）\n" \
        "-- ============================================================\n\n" + block + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只比对，不写文件")
    ap.add_argument("--dry-run", action="store_true", help="打印将写入的区块")
    ap.add_argument("--init", default=str(INIT))
    args = ap.parse_args()

    init_path = Path(args.init)
    text = init_path.read_text(encoding="utf-8")

    try:
        live = Live()
        block = build_block(live)
    except Unavailable as exc:
        print(f"SKIP: 活库不可用（{exc}）——本次未校验 bone-init.sql 与活库的一致性。")
        return 0

    if args.dry_run:
        print(block)
        return 0

    expected = splice(text, block)
    if args.check:
        if expected != text:
            print("FAIL: bone-init.sql 的迁移并入区块与活库不一致。")
            if BEGIN in text:
                cur = text.split(BEGIN, 1)[1].split(END, 1)[0]
                exp = block.split(BEGIN, 1)[1].split(END, 1)[0]
                cur_t = set(re.findall(r"CREATE TABLE `([a-z0-9_]+)`", cur))
                exp_t = set(re.findall(r"CREATE TABLE `([a-z0-9_]+)`", exp))
                for t in sorted(exp_t - cur_t):
                    print(f"  · 缺表 {t}（活库有、init 无）")
                for t in sorted(cur_t - exp_t):
                    print(f"  · 多表 {t}（init 有、活库无）")
                if exp_t == cur_t:
                    print("  · 表集合一致但 DDL 文本不同（列/索引/注释有差异）")
            else:
                print(f"  · bone-init.sql 里没有托管区块（{BEGIN}）")
            print("  修复：python3 scripts/sync-init-from-live.py")
            return 1
        print(f"OK: 迁移并入区块与活库一致（{len(MERGED_SOURCES)} 张表）")
        return 0

    if expected == text:
        print(f"无需改动：区块已与活库一致（{len(MERGED_SOURCES)} 张表）")
        return 0
    init_path.write_text(expected, encoding="utf-8")
    print(f"已写入 {init_path}（{len(MERGED_SOURCES)} 张表）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
