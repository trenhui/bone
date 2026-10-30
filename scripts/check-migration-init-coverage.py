#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-migration-init-coverage.py  (G39 · 全量基线覆盖增量建表终态)

判据：按迁移清单 order 顺序模拟 `CREATE TABLE` / `DROP TABLE` 得到**终态存活表**，
      每一张都必须在 `bone-init.sql` 里存在（或显式登记在 blocked 且说明依据）。

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

为什么要模拟终态（而不是「CREATE 过就必须在」）
------------------------------------------------
第一版判据只看 `CREATE TABLE`，把 `t_claim_case_item` 判成缺口。但它是**废弃表**：
`0047` 建它、`0058` 明确 `DROP TABLE IF EXISTS t_claim_case_item`（注释写明「早期原型，
现行名为 t_claim_item」）。它在任何环境都不存在**是正确终态**。判据不看 DROP 就会把
「已正确收敛」误报成债——而误报会训练人无视门禁，比漏报更坏。故改为按 order 模拟终态。

blocked 与「只可收缩」
---------------------
`init-coverage-baseline.json` 有两个域，语义完全不同：

  · `tables`：**待并入**的存量缺口，**只可收缩**——表并入 init 后必须删除该条，否则判红
    （否则基线变成永久白名单，把「已修好」与「从未修」混为一谈）。
  · `blocked`：**明确决定不并入**的表，每条要 `reason` + `evidence`。典型是「L3 待审批、
    代码零引用」的未落地脚本（如 0067 的 fin_*）。它不是欠账，是**决定**，所以不进 `tables`。
    但决定会被现实推翻：一旦 blocked 表在活库出现（有人真跑了脚本），就说明它已落地，
    必须并入 init 并从 blocked 移除，否则新环境仍然不等价 ⇒ 判红。

新增迁移建表而未并入 init 时**直接判红，不得进基线**。

用法：
  python3 scripts/check-migration-init-coverage.py
  python3 scripts/check-migration-init-coverage.py --no-live          # 跳过活库探测
  python3 scripts/check-migration-init-coverage.py --init <sql> --manifest <json> \
      --baseline <json> --migration-dir <dir>     # 后四项供变异测试

环境变量（活库探测）：BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER / BONE_DB_PASSWORD / BONE_DB_NAME
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys

INIT_REL = "bone-init.sql"
MIGRATION_REL = os.path.join("scripts", "migration")
MANIFEST_REL = os.path.join(MIGRATION_REL, "migration-manifest.json")
BASELINE_REL = os.path.join(MIGRATION_REL, "init-coverage-baseline.json")

CREATE_RX = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?", re.I)
DROP_RX = re.compile(r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?([^;]+)", re.I)
IDENT_RX = re.compile(r"`?([A-Za-z0-9_]+)`?")
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
    """只取 CREATE TABLE（init 侧不需要模拟终态：它就是终态）。"""
    return {
        m.group(1).lower()
        for m in CREATE_RX.finditer(strip_sql_noise(open(path, encoding="utf-8", errors="ignore").read()))
    }


def ops_of(path):
    """按文本位置返回 [(pos, 'create'|'drop', table)]，使同文件内「先删后建」也能正确排序。"""
    text = strip_sql_noise(open(path, encoding="utf-8", errors="ignore").read())
    events = []
    for m in CREATE_RX.finditer(text):
        events.append((m.start(), "create", m.group(1).lower()))
    for m in DROP_RX.finditer(text):
        # DROP TABLE 可一次删多张：DROP TABLE IF EXISTS a, b, c;
        for t in IDENT_RX.findall(m.group(1)):
            events.append((m.start(), "drop", t.lower()))
    events.sort(key=lambda x: x[0])
    return events


def live_tables(db, timeout=5):
    """活库表集合；不可用返回 None（调用方据此 SKIP，不得静默当成空集）。"""
    host = os.environ.get("BONE_DB_HOST", "127.0.0.1")
    port = os.environ.get("BONE_DB_PORT", "3306")
    user = os.environ.get("BONE_DB_USER", "root")
    password = os.environ.get("BONE_DB_PASSWORD", "mysql123")
    sql = ("SELECT table_name FROM information_schema.tables "
           f"WHERE table_schema='{db}' AND table_type='BASE TABLE'")
    try:
        import pymysql  # noqa

        conn = pymysql.connect(host=host, port=int(port), user=user, password=password,
                               database=db, charset="utf8mb4", connect_timeout=timeout)
        try:
            with conn.cursor() as cur:
                cur.execute(sql)
                return {r[0].lower() for r in cur.fetchall()}
        finally:
            conn.close()
    except Exception:
        pass
    cli = os.environ.get("MYSQL_CLIENT") or shutil.which("mysql")
    if not cli:
        for cand in ("/usr/local/mysql/bin/mysql", "/opt/homebrew/bin/mysql", "/usr/bin/mysql"):
            if os.path.exists(cand):
                cli = cand
                break
    if not cli:
        return None
    try:
        out = subprocess.run([cli, f"-h{host}", f"-P{port}", f"-u{user}", f"-p{password}",
                              "-N", "-B", db, "-e", sql],
                             capture_output=True, text=True, timeout=timeout)
        if out.returncode != 0:
            return None
        return {line.strip().lower() for line in out.stdout.splitlines() if line.strip()}
    except Exception:
        return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root")
    ap.add_argument("--init")
    ap.add_argument("--migration-dir")
    ap.add_argument("--manifest")
    ap.add_argument("--baseline")
    ap.add_argument("--no-live", action="store_true", help="跳过活库探测（CI 无库时用）")
    ap.add_argument("--live-db", default=os.environ.get("BONE_DB_NAME", "bone"))
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

    # ---- 按 order 模拟终态 ----
    executable.sort(key=lambda e: (e.get("order") is None, e.get("order") or 0))
    alive = {}          # 终态存活表 -> 建立它的脚本
    dropped_at = {}     # 被 DROP 掉的表 -> (建立脚本, DROP 脚本)
    for e in executable:
        path = os.path.join(mig_dir, e["file"])
        if not os.path.exists(path):
            print(f"❌ 清单登记的 `{e['file']}` 不存在，无法解析其建表")
            return 1
        for _, op, t in ops_of(path):
            if t.startswith(BACKUP_PREFIX):
                continue
            if op == "create":
                alive.setdefault(t, e["file"])
            else:
                created_by = alive.pop(t, None)
                dropped_at[t] = (created_by, e["file"])

    if not alive:
        print("❌ 自检失败：模拟终态存活表为 0——解析已失效，此时任何缺口对比都会恒过")
        return 1

    baseline_doc = json.loads(open(baseline_path, encoding="utf-8").read())
    errors, pending, blocked = [], {}, {}
    for r in baseline_doc.get("tables") or []:
        t = str(r.get("table") or "").strip().lower()
        if not t:
            errors.append("基线条目缺 table")
            continue
        if t in pending:
            errors.append(f"基线里 `{t}` 重复登记")
        if len(str(r.get("reason") or "").strip()) < 8:
            errors.append(f"基线 `{t}` 缺理由（≥8 字：为什么它可以暂时不并入全量）")
        pending[t] = r
    for r in baseline_doc.get("blocked") or []:
        t = str(r.get("table") or "").strip().lower()
        if not t:
            errors.append("blocked 条目缺 table")
            continue
        if t in blocked:
            errors.append(f"blocked 里 `{t}` 重复登记")
        if len(str(r.get("reason") or "").strip()) < 8:
            errors.append(f"blocked `{t}` 缺理由（≥8 字：为什么决定不并入全量）")
        if len(str(r.get("evidence") or "").strip()) < 8:
            errors.append(f"blocked `{t}` 缺证据（evidence：凭什么说它不该/不必并入）")
        blocked[t] = r

    for t in sorted(set(pending) & set(blocked)):
        errors.append(f"`{t}` 同时登记在 tables（待并入）与 blocked（不并入）——二者语义互斥，必须二选一")

    gap = {t: src for t, src in alive.items() if t not in init_tables and t not in blocked}

    for t in sorted(set(gap) - set(pending)):
        also = ""
        if t in dropped_at and dropped_at[t][0]:
            also = f"（注意：它曾建于 {dropped_at[t][0]}，后被 {dropped_at[t][1]} 删除——若这是有意的收敛，请修正判据而非登记基线）"
        errors.append(
            f"`{t}` 由 {gap[t]} 建表且终态存活，但 bone-init.sql 里没有它 ⇒ 新环境灌 init 后缺此表，"
            f"与已迁移环境不等价（新迁移必须同时把结构并入全量；确实暂不并入须登记基线并写理由）{also}"
        )
    for t in sorted(set(pending) - set(gap)):
        why = []
        if t in init_tables:
            why.append("init 已包含")
        if t not in alive:
            why.append("终态已无此表" if t in dropped_at else "已无迁移建它")
        errors.append(
            f"基线（tables）里的 `{t}` 已不需要豁免（{'、'.join(why) or '已覆盖'}）"
            "⇒ **基线只可收缩**，请删除该条"
        )

    # blocked 的两条反向判据
    for t in sorted(blocked):
        if t in init_tables:
            errors.append(
                f"blocked 里的 `{t}` 已在 bone-init.sql 中存在 ⇒ 它已经并入了，必须从 blocked 删除"
                "（否则「不并入」的决定与事实相反，下次真缺口会被这条掩盖）"
            )
        if t not in alive and t not in dropped_at:
            errors.append(f"blocked 里的 `{t}` 没有任何迁移建过它 ⇒ 悬空登记，请核实表名或删除")

    live_note = "未探测"
    if not args.no_live:
        live = live_tables(args.live_db)
        if live is None:
            live_note = f"SKIP（活库 {args.live_db} 不可达）"
            print(f"⚠ 跳过活库探测：{args.live_db} 不可达，未校验 blocked 项是否已落地。")
        else:
            live_note = f"{args.live_db} {len(live)} 张表"
            for t in sorted(blocked):
                if t in live:
                    errors.append(
                        f"blocked 里的 `{t}` 已在活库 {args.live_db} 中存在 ⇒ 脚本已被执行、结构已落地，"
                        "必须并入 bone-init.sql 并从 blocked 移除（否则新环境仍缺表）"
                    )

    if errors:
        print(f"❌ 全量基线覆盖率校验失败：{len(errors)} 项\n")
        for x in errors:
            print("  · " + x)
        print("\n全量基线：" + init_path + "\n覆盖率基线：" + baseline_path)
        return 1

    print(
        "OK: 全量基线覆盖迁移建表终态（init {} 张 / 终态存活 {} 张 / 已覆盖 {} / 待并入 {} / blocked {} / 活库探测 {}）".format(
            len(init_tables), len(alive), len(alive) - len(gap), len(pending), len(blocked), live_note)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
