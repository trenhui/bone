#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
migrate.py — 按 `migration-manifest.json` 的顺序应用迁移，并写执行账本

为什么需要它
------------
清单（`migration-manifest.json`）解决了「该跑哪些、什么顺序」，但**跑没跑过**仍然无人知晓。
`scripts/migration/` 里 84 个脚本没有任何应用记录表；一个环境漏跑 0072，表现不是报错而是
`ClaimCaseTrackRecorder` 打 ERROR 后吞异常——审计流水全丢、页面照常 200。这类「静默少跑」
只能靠显式账本发现，不能靠人记。

所以这里做与 Flyway/Liquibase 同构的三件事，不引入依赖：
  1. **账本表** `schema_migration_journal`（工具自有基础设施表，不是业务表，故不进
     bone-init.sql，也不参与 HC-008 必备列口径——由本工具按需 CREATE IF NOT EXISTS）；
  2. **校验和**：记录每个脚本的 sha256。已应用的脚本被改动 = 环境与仓库分叉，
     `--status` 判红、`--apply` 拒绝继续（否则「谁改了已发的迁移」永远查不出来）；
  3. **顺序**：只按 manifest 的 `order` 跑，不按文件名排序（重号参见 manifest 的
     `ordinalConflicts`）。

执行方式用 `mysql` 客户端而不是自己切语句：`scripts/migration/` 里有 `DELIMITER $$`
存储过程（0078 等），自制 SQL 切分器在 `;` 与 `$$` 之间出错的方式很难被发现，
而客户端是与 DBA 手工执行完全一致的参考实现。

安全边界
--------
· `--status` 是默认动作，纯读。
· `--apply` / `--baseline` 必须显式给出；且目标主机不是本机时还需 `--force-nonlocal`——
  本仓红线是不由 AI 执行生产库变更，这个开关让「打错 -h」在动 DDL 之前就被拦下。
· 不做 DROP DATABASE、不做回滚（回滚属各脚本自带「回滚」段，需人工判断）。

重要：本清单是**存量环境的增量顺序**，不是「新库初始化脚本」
--------------------------------------------------------
`bone-init.sql` 是当前全量结构（含 CREATE DATABASE bone / USE bone），新环境只需灌它，
不需要再跑这些迁移。反过来，把全部 84 个脚本对空库跑一遍必然失败——2026-10-30 实测
65 成功 / 19 失败，失败项多为 `Duplicate column`（对象早已并入基线，如 0002 的 app_id）
或脚本自带 SIGNAL 校验发现目标状态不满足（如 0078 要求理赔菜单按租户×10 全覆盖）。
所以 CI 不做「空库全量 apply」，只在清单层做静态校验 + 账本层做功能自测。

用法：
  python3 scripts/migrate.py --status
  python3 scripts/migrate.py --baseline      # 存量环境一次性登记既有状态（不执行脚本）
  python3 scripts/migrate.py --apply
  python3 scripts/migrate.py --apply --to 20          # 只应用到 order<=20（分段排查）
  python3 scripts/migrate.py --apply --db bone_migverify

连接来源（与 verify_0052.py 一致）：环境变量 MYSQL_* > 仓库 .env 的 BONE_DB_* > 本地默认值
  MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DB
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
MIG_DIR = os.path.join(HERE, "migration")
MANIFEST = os.path.join(MIG_DIR, "migration-manifest.json")
JOURNAL = "schema_migration_journal"
LOCAL_HOSTS = {"127.0.0.1", "localhost", "::1", "127.0.0.1:3306"}

JOURNAL_DDL = """
CREATE TABLE IF NOT EXISTS {t} (
  order_no     INT           NOT NULL COMMENT '应用顺序（取自 migration-manifest.json）',
  file         VARCHAR(200)  NOT NULL COMMENT '脚本文件名',
  sha256       CHAR(64)      NOT NULL COMMENT '应用时的内容校验和（用于发现已发脚本被改动）',
  applied_at   DATETIME(3)   NOT NULL,
  duration_ms  INT           NOT NULL,
  success      TINYINT(1)    NOT NULL,
  error_msg    VARCHAR(500)  DEFAULT NULL,
  PRIMARY KEY (order_no),
  UNIQUE KEY uk_file (file)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='迁移执行账本（scripts/migrate.py 维护，非业务表）'
""".format(t=JOURNAL)


def load_env():
    env = {}
    path = os.path.join(HERE, "..", ".env")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if "=" not in line or line.startswith("#"):
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def resolve_conn():
    e = load_env()

    def pick(env_key, dotenv_key, default):
        return os.getenv(env_key) or e.get(dotenv_key) or default

    return {
        "host": pick("MYSQL_HOST", "BONE_DB_HOST", "127.0.0.1"),
        "port": str(pick("MYSQL_PORT", "BONE_DB_PORT", "3306")),
        "user": pick("MYSQL_USER", "BONE_DB_USERNAME", "root"),
        "password": pick("MYSQL_PASSWORD", "BONE_DB_PASSWORD", ""),
        "db": pick("MYSQL_DB", "BONE_DB_NAME", "bone"),
    }


def mysql_bin():
    for cand in (os.getenv("MYSQL_BIN"), "/usr/local/mysql/bin/mysql", shutil.which("mysql")):
        if cand and os.path.exists(cand):
            return cand
    sys.exit("❌ 找不到 mysql 客户端。请安装 mysql-client，或设 MYSQL_BIN 指向它。")


class Client:
    """用临时 defaults-extra-file 传凭据：避免 -pPASS 出现在 ps 里。"""

    def __init__(self, conn):
        self.conn = conn
        fd, self.cnf = tempfile.mkstemp(prefix="bone-migrate-", suffix=".cnf")
        os.chmod(self.cnf, 0o600)
        with os.fdopen(fd, "w") as f:
            f.write("[client]\n")
            f.write("host=%s\nport=%s\nuser=%s\n" % (conn["host"], conn["port"], conn["user"]))
            if conn["password"]:
                f.write("password=%s\n" % conn["password"])
        self.bin = mysql_bin()

    def close(self):
        try:
            os.unlink(self.cnf)
        except OSError:
            pass

    def _cmd(self, database=True):
        cmd = [self.bin, "--defaults-extra-file=" + self.cnf, "--default-character-set=utf8mb4", "-N", "-B"]
        if database:
            cmd.append(self.conn["db"])
        return cmd

    def query(self, sql, database=True):
        r = subprocess.run(self._cmd(database) + ["-e", sql], capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError(r.stderr.strip() or "mysql 执行失败")
        return [line.split("\t") for line in r.stdout.strip().split("\n") if line]

    def run_file(self, path):
        with open(path, "rb") as f:
            r = subprocess.run(self._cmd() + ["--show-warnings"], stdin=f, capture_output=True)
        return r.returncode, (r.stderr.decode("utf-8", "ignore").strip())


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def load_manifest():
    if not os.path.exists(MANIFEST):
        sys.exit(f"❌ 清单不存在：{MANIFEST}")
    doc = json.loads(open(MANIFEST, encoding="utf-8").read())
    items = [e for e in doc.get("entries", []) if isinstance(e.get("order"), int)]
    items.sort(key=lambda e: e["order"])
    return doc, items


def ensure_journal(cli):
    cli.query(JOURNAL_DDL)


def read_journal(cli):
    """读账本；表还不存在时视为空账本。

    只把「表不存在」(1146) 当空账本。连接失败/权限不足若也当空，`--status` 会把一个
    根本没连上的库报成「84 条待应用」——那是把「查不到」伪装成「没跑过」的假绿。
    """
    rows = {}
    try:
        out = cli.query(f"SELECT order_no, file, sha256, success FROM {JOURNAL}")
    except RuntimeError as e:
        msg = str(e)
        if "1146" in msg or "doesn't exist" in msg or "does not exist" in msg:
            return rows
        raise
    for r in out:
        rows[int(r[0])] = {"file": r[1], "sha": r[2], "success": r[3] == "1"}
    return rows


def cmd_status(cli, items):
    apply_state = read_journal(cli)
    drift, missing, pending, ok = [], [], [], 0
    files_in_manifest = {e["file"] for e in items}
    for e in items:
        row = apply_state.get(e["order"])
        cur = sha256_of(os.path.join(MIG_DIR, e["file"]))
        if row is None:
            pending.append(e)
        elif not row["success"]:
            pending.append(e)
        elif row["sha"] != cur:
            drift.append((e, row["sha"][:12], cur[:12]))
        elif row["file"] != e["file"]:
            missing.append((e, row["file"]))
        else:
            ok += 1
    for order_no, row in sorted(apply_state.items()):
        if row["file"] not in files_in_manifest:
            missing.append(({"order": order_no, "file": row["file"]}, "已不在清单中"))

    print("账本 {} @ {}:{}/{}".format(JOURNAL, cli.conn["host"], cli.conn["port"], cli.conn["db"]))
    print("已应用且一致 {} / 待应用 {} / 校验和不符 {} / 账本残留 {}".format(ok, len(pending), len(drift), len(missing)))
    if pending:
        print("\n待应用：")
        for e in pending[:20]:
            print("  #{:<3} {}  {}".format(e["order"], e["file"], e.get("summary", "")[:60]))
        if len(pending) > 20:
            print("  ... 另有 {} 条".format(len(pending) - 20))
    if drift:
        print("\n❌ 校验和不符（已应用后脚本被改动，环境与仓库已分叉）：")
        for e, was, now in drift:
            print("  #{:<3} {}  记账 {} → 现状 {}".format(e["order"], e["file"], was, now))
        print("  处置：确认库中已应用的是哪一版；脚本只可新增不可回改（改动请另起新号脚本）。")
    if missing:
        print("\n❌ 账本残留（账本里有、清单里没有）：")
        for e, extra in missing:
            print("  #{:<3} {}  {}".format(e["order"], e["file"], extra))
    if not drift and not missing:
        print("\nOK: 账本与清单一致" + ("，可以 --apply" if pending else "，无待应用项"))
    return 1 if (drift or missing) else 0


def cmd_apply(cli, items, upto, allow_nonlocal):
    if cli.conn["host"] not in LOCAL_HOSTS and not allow_nonlocal:
        sys.exit(
            "❌ 目标主机 {} 不是本机。本仓红线：AI 不执行生产库变更。\n"
            "   确需在远端执行请加 --force-nonlocal，并由人复核后再运行。".format(cli.conn["host"])
        )
    ensure_journal(cli)
    state = read_journal(cli)
    todo = []
    for e in items:
        if upto and e["order"] > upto:
            continue
        row = state.get(e["order"])
        path = os.path.join(MIG_DIR, e["file"])
        cur = sha256_of(path)
        if row and row["success"]:
            if row["sha"] != cur:
                sys.exit(
                    "❌ #{} {} 已应用但内容已变（记账 {} / 现状 {}）。\n"
                    "   已发脚本不可回改：请还原该文件，并把改动另起新号脚本。".format(
                        e["order"], e["file"], row["sha"][:12], cur[:12])
                )
            continue
        todo.append((e, cur))

    if not todo:
        print("无待应用项（manifest 内 {} 个脚本均已记账）。".format(len(items)))
        return 0

    print("将按 order 应用 {} 个脚本到 {}:{}/{}：".format(
        len(todo), cli.conn["host"], cli.conn["port"], cli.conn["db"]))
    for e, _ in todo:
        print("  #{:<3} {}".format(e["order"], e["file"]))
    print()
    failed = None
    for e, cur in todo:
        path = os.path.join(MIG_DIR, e["file"])
        t0 = time.monotonic()
        rc, err = cli.run_file(path)
        ms = int((time.monotonic() - t0) * 1000)
        ok = rc == 0
        cli.query(
            "INSERT INTO {} (order_no, file, sha256, applied_at, duration_ms, success, error_msg) "
            "VALUES ({}, '{}', '{}', NOW(3), {}, {}, {}) "
            "ON DUPLICATE KEY UPDATE sha256=VALUES(sha256), applied_at=VALUES(applied_at), "
            "duration_ms=VALUES(duration_ms), success=VALUES(success), error_msg=VALUES(error_msg)".format(
                JOURNAL, e["order"], e["file"].replace("'", "''"), cur, ms, 1 if ok else 0,
                "NULL" if ok else "'" + err[:480].replace("\\", "\\\\").replace("'", "''") + "'",
            )
        )
        print("  {} #{:<3} {}  {}ms".format("OK  " if ok else "FAIL", e["order"], e["file"], ms))
        if not ok:
            print("\n❌ 脚本失败（后续脚本不再执行，避免在半截状态上继续叠加）：")
            print(err[:2000])
            failed = e
            break

    if failed:
        print("\n处置：修脚本后重跑；已成功项已记账，不会重复应用。")
        return 1
    print("\n全部应用完成，账本已更新。")
    return 0


def cmd_baseline(cli, items):
    """把当前环境的既有状态一次性登记为「已应用」，不执行任何脚本。

    用途：本账本上线前就有若干环境（本地 dev / 测试库）早已按人工方式跑过这些脚本，
    若不登记，`--status` 会把 84 条全报成「待应用」，账本就没人看了。

    这是**承认历史**，不是验证历史：登记后的 sha256 取自当前仓库文件，
    所以一旦有人改动已发脚本，`--status` 立刻报「校验和不符」。
    """
    ensure_journal(cli)
    rows = read_journal(cli)
    recorded = 0
    for e in items:
        if e["order"] in rows and rows[e["order"]]["success"]:
            continue
        cur = sha256_of(os.path.join(MIG_DIR, e["file"]))
        cli.query(
            "INSERT INTO {} (order_no, file, sha256, applied_at, duration_ms, success, error_msg) "
            "VALUES ({}, '{}', '{}', NOW(3), 0, 1, NULL) "
            "ON DUPLICATE KEY UPDATE sha256=VALUES(sha256), success=1, error_msg=NULL".format(
                JOURNAL, e["order"], e["file"].replace("'", "''"), cur)
        )
        recorded += 1
    print("已把 {} 条登记为「已应用」（未执行任何脚本）。此后 --status 方可反映真实待办。".format(recorded))
    print("注意：这是承认既有状态，不是验证——若要确认对象真的存在，请单独跑对应 verify 脚本。")
    return 0


def main():
    ap = argparse.ArgumentParser(description="按 migration-manifest.json 顺序应用迁移并记账")
    ap.add_argument("--status", action="store_true", help="只读：比对账本与清单（默认动作）")
    ap.add_argument("--apply", action="store_true", help="应用待应用脚本（会写库）")
    ap.add_argument("--baseline", action="store_true", help="把现有环境登记为「已应用」，不执行脚本（每环境一次性）")
    ap.add_argument("--to", type=int, help="只应用到 order<=N")
    ap.add_argument("--db", help="覆盖目标库名")
    ap.add_argument("--force-nonlocal", action="store_true", help="允许对非本机库执行（需人工复核）")
    args = ap.parse_args()

    conn = resolve_conn()
    if args.db:
        conn["db"] = args.db
    _, items = load_manifest()

    cli = Client(conn)
    try:
        if args.baseline:
            if conn["host"] not in LOCAL_HOSTS and not args.force_nonlocal:
                sys.exit(
                    "❌ 目标主机 {} 不是本机。--baseline 会把全部脚本登记为已应用，"
                    "在远端库上等于替他写下无从核实的结论，需 --force-nonlocal 且人工复核。".format(conn["host"])
                )
            return cmd_baseline(cli, items)
        if args.apply:
            return cmd_apply(cli, items, args.to, args.force_nonlocal)
        return cmd_status(cli, items)
    except RuntimeError as e:
        # 连不上 / 权限不足 / SQL 错：明确报错并退 1，不要退化成「待应用」这种看似正常的输出。
        print("❌ 数据库操作失败：{}".format(e))
        return 1
    finally:
        cli.close()


if __name__ == "__main__":
    sys.exit(main())
