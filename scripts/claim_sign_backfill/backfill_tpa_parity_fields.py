#!/usr/bin/env python3
"""把 TPA 签收源数据中的可读保司名称回填到 0147 新增列。

为什么需要它
------------
0147 只加列不造数据；TPA 历史批次的保险公司/分公司/投保公司/紧急程度源站已有，
但字段混用编码值与中文名（如 412312 / yccc）。本脚本只回填含中文的可读名称，
编码值与 UUID 一律跳过，避免把源站脏数据带进 Bone 签收台。

幂等：只更新目标列全为 NULL 的行；重跑不会覆盖人工修正值。

用法：python3 scripts/claim_sign_backfill/backfill_tpa_parity_fields.py
"""
import json
import os
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "outputs" / "tpa-claim-crawl" / "signRecord_all.json"
ENV = ROOT / ".env"


def load_env():
    """worktree 通常没有 .env，回退到主工作树读取。"""
    path = ENV if ENV.exists() else ROOT.parent.parent / ".env"
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.strip().startswith("#"):
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def readable(value):
    if value is None:
        return None
    text = str(value).strip()
    if not text or len(text) > 128:
        return None
    if not any("\u4e00" <= char <= "\u9fff" for char in text):
        return None
    return text


def main():
    if not SOURCE.exists():
        print("MISSING_SOURCE", SOURCE)
        return 1
    env = load_env()
    payload = json.loads(SOURCE.read_text(encoding="utf-8"))
    rows = payload["data"]["data"]
    conn = pymysql.connect(
        host=env.get("BONE_DB_HOST", "127.0.0.1"),
        port=int(env.get("BONE_DB_PORT", "3306")),
        user=env.get("BONE_DB_USERNAME", "root"),
        password=env.get("BONE_DB_PASSWORD", ""),
        database=env.get("BONE_DB_NAME", "bone"),
        autocommit=True,
    )
    updated = 0
    skipped = 0
    with conn.cursor() as cur:
        for row in rows:
            main_row = row.get("main") or {}
            values = (
                readable(main_row.get("insuranceCompanyCn") or main_row.get("insuranceCompany")),
                readable(
                    main_row.get("insuranceSubsidiaryCn")
                    or main_row.get("insuranceSubsidiary")
                ),
                readable(main_row.get("insuringAgencyCn") or main_row.get("insuringAgency")),
                readable(main_row.get("emergency")),
            )
            if not any(values):
                skipped += 1
                continue
            cur.execute(
                """UPDATE t_claim_sign_batch
                   SET insurance_company = COALESCE(insurance_company, %s),
                       insurance_subsidiary = COALESCE(insurance_subsidiary, %s),
                       insuring_agency = COALESCE(insuring_agency, %s),
                       emergency = COALESCE(emergency, %s)
                   WHERE batch_no = %s""",
                (*values, main_row.get("batchNo")),
            )
            updated += cur.rowcount
    conn.close()
    print("TPA_PARITY_BACKFILL_OK updated=%d skipped=%d" % (updated, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
