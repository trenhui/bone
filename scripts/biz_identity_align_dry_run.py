#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
biz_identity_align_dry_run.py —— 存量保单业务主体对齐 dry-run（只输出，不落库）

背景
----
t_claim_policy.biz_identity_code 此前是自由文本（insurer_name 的别名），七表内核上线后
它成为映射层的键。存量保单需要人工对齐到 t_claim_insurer.insurer_code；本脚本按
「主体码精确 → 主体名精确 → 主体名/简称归一化包含」三级匹配生成对齐清单，
供运营确认后走 updatePolicy（写入侧已有 ACTIVE 主体校验兜底）。

为什么是 dry-run 而不是直接 UPDATE
----------------------------------
模糊匹配必然有歧义（"永诚财险" 既可能是法人也可能是分公司），机器只出候选、人做裁决——
与映射层「模糊匹配只用于离线出候选、不进解析路径」是同一条铁律。

用法
----
    BONE_DB_PASSWORD=... python3 scripts/biz_identity_align_dry_run.py [--csv out.csv]

环境变量同 sync-init-from-live.py：BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER /
BONE_DB_PASSWORD / BONE_DB_NAME。
"""
from __future__ import annotations

import argparse
import csv
import os
import re
import sys
from pathlib import Path

MATCH_EXACT_CODE = "EXACT_CODE"
MATCH_EXACT_NAME = "EXACT_NAME"
MATCH_NORM_CONTAINS = "NORM_CONTAINS"
MATCH_NONE = "NONE"


def _env(key: str, default: str) -> str:
    return os.environ.get(key, default)


def normalize(name: str) -> str:
    """归一化：去空白/全角括号/股份后缀等，与 NameNormalizer 同思路的 Python 侧近似实现。"""
    s = (name or "").strip()
    s = s.replace("（", "(").replace("）", ")")
    s = re.sub(r"\s+", "", s)
    for suffix in (
        "股份有限公司",
        "有限责任公司",
        "有限公司",
        "保险股份有限公司",
    ):
        if s.endswith(suffix):
            s = s[: -len(suffix)]
    return s


def connect():
    import pymysql

    return pymysql.connect(
        host=_env("BONE_DB_HOST", "127.0.0.1"),
        port=int(_env("BONE_DB_PORT", "3306")),
        user=_env("BONE_DB_USER", "root"),
        password=_env("BONE_DB_PASSWORD", "mysql123"),
        database=_env("BONE_DB_NAME", "bone"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", help="额外输出 CSV 路径（默认仅打印到 stdout）")
    args = parser.parse_args()

    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT DISTINCT COALESCE(biz_identity_code,'') AS code,"
                " COALESCE(insurer_name,'') AS name, COUNT(*) AS n"
                " FROM t_claim_policy WHERE deleted=0 GROUP BY code, name ORDER BY n DESC"
            )
            policies = cur.fetchall()
            cur.execute(
                "SELECT insurer_code, insurer_name, insurer_short_name, status"
                " FROM t_claim_insurer WHERE deleted=0"
            )
            insurers = cur.fetchall()
    finally:
        conn.close()

    norm_insurers = []
    for ins in insurers:
        names = [ins["insurer_name"], ins["insurer_short_name"]]
        norm_insurers.append(
            {
                "code": ins["insurer_code"],
                "status": ins["status"],
                "norm_names": [normalize(n) for n in names if n],
            }
        )

    rows = []
    for p in policies:
        key = p["code"] or normalize(p["name"])
        if not key:
            continue
        match, candidate = MATCH_NONE, None
        for ins in insurers:
            if p["code"] and ins["insurer_code"] == p["code"].strip():
                match, candidate = MATCH_EXACT_CODE, ins
                break
        if not candidate:
            for ins in insurers:
                if p["name"] and ins["insurer_name"] == p["name"].strip():
                    match, candidate = MATCH_EXACT_NAME, ins
                    break
        if not candidate:
            norm_key = normalize(p["name"] or p["code"])
            for ins in norm_insurers:
                if any(norm_key and norm_key in n for n in ins["norm_names"]):
                    match, candidate = MATCH_NORM_CONTAINS, ins
                    break
        rows.append(
            {
                "policy_value": p["code"] or p["name"],
                "policy_name": p["name"],
                "policy_count": p["n"],
                "match": match,
                "suggested_code": (candidate or {}).get("insurer_code", ""),
                "suggested_name": (candidate or {}).get("insurer_name", ""),
                "insurer_status": (candidate or {}).get("status", ""),
            }
        )

    matched = sum(1 for r in rows if r["match"] != MATCH_NONE)
    print(f"distinct 存量取值 {len(rows)} 个；自动匹配 {matched} 个；未匹配 {len(rows)-matched} 个\n")
    for r in rows:
        print(
            f"[{r['match']:>13}] {r['policy_value']!r} × {r['policy_count']} 单"
            f" -> {r['suggested_code'] or '(需人工裁决)'} {r['insurer_status']}"
        )

    if args.csv:
        out = Path(args.csv)
        out.parent.mkdir(parents=True, exist_ok=True)
        with out.open("w", newline="", encoding="utf-8-sig") as fh:
            writer = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            writer.writeheader()
            writer.writerows(rows)
        print(f"\nCSV 已写出: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
