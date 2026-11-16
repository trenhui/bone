#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
claim_assign_gray_report.py —— AUTO_ASSIGN 灰度窗口报告（按小时命中率 + 回落计数）

判据（design「灰度/回退」与批 1 任务 4.2）
----------------------------------------
* 规则命中率 ≥ 0.90：低于阈值退出码 1，可直接接监控（非零即告警）；
* 回落计数按小时可观测：逐小时同时给出 no_rule（没配规则）与 all_ineligible
  （配了但目标不可用）——两者处置不同，合并统计会引导错误排查方向。

为什么单独一个脚本而不是塞进 check.sh
------------------------------------
命中率是**环境数据指标**，不是代码判据：未配规则的干净环境命中率天然为 0。
把它做成常驻门禁会让「新装环境」永远判红。故以运维报告形式提供，由调度/值班按窗口执行
（`--min-hit-rate 0` 可只看数据不判定）。

用法
----
    BONE_DB_PASSWORD=... python3 scripts/ops/claim_assign_gray_report.py --days 7
    BONE_DB_PASSWORD=... python3 scripts/ops/claim_assign_gray_report.py --since "2026-11-15 00:00:00"

退出码：0 = 达标或无数据；1 = 命中率低于阈值；2 = 参数/连接错误。
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import re
import sys
from pathlib import Path

import pymysql

SQL_FILE = Path(__file__).with_name("claim-assign-gray-stats.sql")
SINCE_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$")


def _env(key: str, default: str) -> str:
    return os.environ.get(key, default)


def connect():
    return pymysql.connect(
        host=_env("BONE_DB_HOST", "127.0.0.1"),
        port=int(_env("BONE_DB_PORT", "3306")),
        user=_env("BONE_DB_USER", "root"),
        password=_env("BONE_DB_PASSWORD", "mysql123"),
        database=_env("BONE_DB_NAME", "bone"),
        charset="utf8mb4",
        autocommit=True,
    )


def render_since(since: str) -> str:
    """占位符替换前先校验格式：字符串直接拼进 SQL，必须挡注入（哪怕来源是命令行）。"""
    if not SINCE_PATTERN.match(since):
        raise ValueError(f"--since 必须是 'YYYY-MM-DD HH:MM:SS'，收到 {since!r}")
    dt.datetime.strptime(since, "%Y-%m-%d %H:%M:%S")
    return since


def main() -> int:
    parser = argparse.ArgumentParser(description="AUTO_ASSIGN 灰度窗口报告")
    parser.add_argument("--days", type=int, default=7, help="统计最近 N 天（默认 7）")
    parser.add_argument("--since", default=None, help="显式起点 'YYYY-MM-DD HH:MM:SS'（优先于 --days）")
    parser.add_argument(
        "--min-hit-rate",
        type=float,
        default=0.90,
        help="命中率下限（默认 0.90；<=0 表示只报数、不启用阈值判定）",
    )
    parser.add_argument("--tenant", type=int, default=None, help="只统计指定租户（下推到 SQL，不是事后筛选）")
    args = parser.parse_args()

    try:
        since = args.since or (
            dt.datetime.now() - dt.timedelta(days=max(1, args.days))
        ).strftime("%Y-%m-%d %H:%M:%S")
        since = render_since(since)
    except ValueError as exc:
        print(f"❌ {exc}")
        return 2

    # 租户过滤必须真下推：传了 --tenant 却统计全租户，比没有这个参数更危险。
    tenant_filter = "" if args.tenant is None else f"AND tenant_id = {int(args.tenant)}"

    sql = (
        SQL_FILE.read_text(encoding="utf-8")
        .replace("{{SINCE}}", since)
        .replace("{{TENANT_FILTER}}", tenant_filter)
    )
    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
            rows = cur.fetchall()
    except pymysql.MySQLError as exc:
        print(f"❌ 查询失败：{exc}")
        return 2
    finally:
        conn.close()

    if not rows:
        print(f"OK: 自 {since} 起无分配记录（开关可能仍关闭，或窗口内无新任务）")
        return 0

    print(f"灰度窗口自 {since} 起，逐小时统计：")
    print(f"{'hour':<18}{'total':>8}{'matched':>9}{'no_rule':>9}{'ineligible':>12}{'hit_rate':>10}")
    tot = mat = 0
    for hour, total, matched, no_rule, ineligible, hit_rate in rows:
        tot += int(total)
        mat += int(matched or 0)
        print(
            f"{hour:<18}{int(total):>8}{int(matched or 0):>9}"
            f"{int(no_rule or 0):>9}{int(ineligible or 0):>12}{float(hit_rate or 0):>10.4f}"
        )
    overall = (mat / tot) if tot else 0.0
    print(f"合计：total={tot} matched={mat} overall_hit_rate={overall:.4f}")

    if args.min_hit_rate > 0 and overall < args.min_hit_rate:
        print(
            f"❌ 命中率 {overall:.4f} 低于阈值 {args.min_hit_rate:.2f}"
            "：先看 no_rule（规则没配）与 all_ineligible（目标不可用）哪一类占多数，再定向处置。"
        )
        return 1
    if args.min_hit_rate > 0:
        print(f"✅ 命中率 {overall:.4f} 达标（阈值 {args.min_hit_rate:.2f}）")
    else:
        # 阈值关闭时不能说「达标」——那会让值班同学把 0.00 的命中率读成健康。
        print("ℹ️  未启用阈值判定（--min-hit-rate<=0），以上仅为观测数据")
    return 0


if __name__ == "__main__":
    sys.exit(main())
