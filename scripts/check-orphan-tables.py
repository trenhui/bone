#!/usr/bin/env python3
"""校验「已软删实体的运行时物理表未随实体删除而清理」——元数据平台的定义即真相门禁。

背景（问题实证，非推断）
------------------------
2026-10 E2E 联调实测：删除实体只软删 `meta_entity` 一行与动作定义，**不删物理表**
（MetaEntityApplicationService#deleteEntity 无 DROP TABLE 动作，PhysicalStructureGateway
也没有 dropTable 用例）。后果：
  · 数据库里积压 36 张无主物理表（t_verify_e2e_* / t_vfix_* / t_exp_inst_* / md_* E2E 残留），
    占用空间且让「发布即建表」的承诺失真（看得见的表 ≠ 在用的模型）；
  · 人工巡检必然腐化——本轮 36 张就是连续多轮 E2E 累积、无人发现的。

判据（为什么不是「无在用实体引用」）
------------------------------------
朴素判据（物理表不在任何在用 meta_entity.table_name 中）会把 iam_* / gen_* / bp_* /
exts_* 等**其它模块自建表**全部误报（本库实测 160+ 假阳性）——元数据平台只拥有
「经 align 建过」的表，而那张表的身份证据就是 meta_entity 行本身。精确判据：

    orphan = 物理表 ∩ 已软删实体(deleted=1)的 table_name − 在用实体(deleted=0)的 table_name

即「物理表存在、且其唯一可能的建模来源已软删、且没有同名在用实体顶替」。零跨模块误报、
零已证泄漏漏报（36/36 实测命中）。

安全边界
--------
本门禁**只报告、不删除**：DROP 是 L3 破坏性操作，留给 DBA/管理员显式执行
（与 ADR expand/contract、大表 gh-ost/pt-osc 策略同口径）。清理命令随报告打印。

环境降级（显式，不静默）
------------------------
pre-commit 门禁不能因「本机没有数据库客户端」而失败：依序尝试 pymysql → mysql CLI
（$MYSQL_CLIENT + PATH + 常见安装路径）→ 都不可用则打印 SKIP 理由并退出 0。
有客户端而连不上库同样 SKIP——门禁守护的是「有库可查时不许积压」，
不是「每台机器都必须有库」。跳过必须**可见**（静默跳过比跑错更危险）。

用法：
  python3 scripts/check-orphan-tables.py           # 报告；有孤儿退出码 1
  python3 scripts/check-orphan-tables.py --json    # JSON 汇总（供 CI）
  环境变量：BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER / BONE_DB_PASSWORD / BONE_DB_NAME
            （默认 127.0.0.1:3306 root/mysql123 bone，与本仓库本地联调约定一致）
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

DB = {
    "host": os.environ.get("BONE_DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("BONE_DB_PORT", "3306")),
    "user": os.environ.get("BONE_DB_USER", "root"),
    "password": os.environ.get("BONE_DB_PASSWORD", "mysql123"),
    "name": os.environ.get("BONE_DB_NAME", "bone"),
}

MYSQL_CLI_CANDIDATES = [
    os.environ.get("MYSQL_CLIENT", ""),
    "mysql",
    "/usr/local/mysql-8.0.31-macos12-x86_64/bin/mysql",
    "/usr/local/mysql/bin/mysql",
    "/opt/homebrew/bin/mysql",
]


def find_mysql_cli():
    for cand in MYSQL_CLI_CANDIDATES:
        if not cand:
            continue
        found = shutil.which(cand) or (
            cand if os.path.isfile(cand) and os.access(cand, os.X_OK) else None
        )
        if found:
            return found
    return None


def query_via_pymysql(sql: str):
    import pymysql  # 延迟导入：无 pymysql 的环境走 CLI 分支

    conn = pymysql.connect(
        host=DB["host"],
        port=DB["port"],
        user=DB["user"],
        password=DB["password"],
        database=DB["name"],
    )
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
            return list(cur.fetchall())
    finally:
        conn.close()


def query_via_cli(cli: str, sql: str):
    # -N 去表头/边框，--batch 制表符分隔；密码经 MYSQL_PWD 传入避免 argv 泄露
    env = dict(os.environ, MYSQL_PWD=DB["password"])
    proc = subprocess.run(
        [
            cli,
            "-h",
            DB["host"],
            "-P",
            str(DB["port"]),
            "-u",
            DB["user"],
            "-N",
            "--batch",
            DB["name"],
            "-e",
            sql,
        ],
        capture_output=True,
        text=True,
        env=env,
        timeout=30,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or f"mysql CLI exit {proc.returncode}")
    rows = []
    for line in proc.stdout.splitlines():
        if line.strip():
            rows.append(tuple(line.split("\t")))
    return rows


def run_query(sql: str):
    """依序尝试 pymysql → mysql CLI；全部不可用抛 RuntimeError（调用方转 SKIP）。"""
    errors = []
    cli = find_mysql_cli()
    runners = [("pymysql", query_via_pymysql)]
    if cli:
        runners.append((f"cli({cli})", lambda s: query_via_cli(cli, s)))
    for name, runner in runners:
        try:
            return runner(sql)
        except ImportError as e:
            errors.append(f"{name}: {e}")
        except Exception as e:  # noqa: BLE001 - 汇总两个通道的失败原因
            errors.append(f"{name}: {e}")
    raise RuntimeError("; ".join(errors))


def main() -> int:
    parser = argparse.ArgumentParser(description="孤儿运行时物理表门禁（只报告不删除）")
    parser.add_argument("--json", action="store_true", help="JSON 输出（供 CI 汇总）")
    args = parser.parse_args()

    try:
        tables = {r[0] for r in run_query(
            f"SELECT table_name FROM information_schema.TABLES WHERE TABLE_SCHEMA = '{DB['name']}'"
        )}
        entity_rows = run_query("SELECT table_name, id, code, deleted FROM meta_entity")
    except Exception as e:  # noqa: BLE001
        reason = f"数据库不可达或无可用客户端（{e}）"
        if args.json:
            print(json.dumps({"status": "skipped", "reason": reason}, ensure_ascii=False))
        else:
            print(f"SKIP 孤儿表门禁：{reason}。门禁仅在可连接时生效。")
        return 0

    dead: dict[str, list[str]] = {}
    live: set[str] = set()
    for table_name, eid, code, deleted in entity_rows:
        if not table_name:
            continue
        if str(deleted) == "1":
            dead.setdefault(table_name, []).append(f"{code}(id={eid})")
        else:
            live.add(table_name)

    orphans = sorted(name for name in set(tables) & set(dead) if name not in live)

    if args.json:
        print(
            json.dumps(
                {
                    "status": "ok" if not orphans else "violation",
                    "orphanCount": len(orphans),
                    "orphans": [{"table": n, "deletedEntities": dead[n]} for n in orphans],
                },
                ensure_ascii=False,
            )
        )
        return 1 if orphans else 0

    if not orphans:
        print("OK 无孤儿运行时物理表（物理表与在用实体定义一致）。")
        return 0

    print(f"发现 {len(orphans)} 张孤儿运行时物理表（实体已软删，表未清理）：")
    for name in orphans:
        print(f"  - {name}  <- 已软删实体: {', '.join(dead[name])}")
    print("清理（L3，DBA/管理员显式执行；确认无历史价值后）：")
    for name in orphans:
        print(f"  DROP TABLE {name};")
    print("根治：删除实体时应联动 DROP 物理表（当前仅软删定义，见 P1-③ 收口项）。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
