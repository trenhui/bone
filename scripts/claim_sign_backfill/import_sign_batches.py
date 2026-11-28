#!/usr/bin/env python3
"""把 TPA 抓到的团险签收批次导入本地 Bone 开发库，并回填自动建案要素。

数据源（只读抓取产物）：
  signRecord_all.json            23 个签收批次
  signRecord_members.json        4 个有名单批次的 15 条赔案关系
  signRecord_out_insure_time.json 逐赔案真实出险时间（源站详情接口，只有 1 条非空）

写入原则：
  · 仅本地开发库（.env 指向 127.0.0.1:3306/bone），单事务；默认 dry-run，--apply 才写；
  · 批次按 business_key = 'pk-tpa-sign-<TPA批次号>' 幂等；明细按 business_key
    = 'pk-tpa-sign-<批次号>-<赔案号>' 幂等；案件按 claim_no 幂等；
  · 姓名/证件号用源站 outInsureName/outInsureIdentityNo 原值明文写入（用户显式裁决）；
  · 源站未提供出险时间时，显式使用源站 createTime 兜底，并在签收轨迹标注，不冒充真实出险事实；
  · 已有本地案件只回写关联，不重复建案；证件号缺失的明细落到 AWAIT_MANUAL 等人工补录；
  · tenant_id = 0（与本地 admin JWT 一致）；ID 用 7500xxxx 段，避开雪花 ID；
  · 每批写一条 IMPORT_PK_TPA 轨迹；每条明细写一条自动建案结果轨迹，append-only。
"""

from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

import pymysql

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ROOT = PROJECT_ROOT / "outputs" / "tpa-claim-crawl"
ENV = PROJECT_ROOT / ".env"


def load_env() -> dict:
    out = {}
    for line in ENV.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def dt(value):
    if not value:
        return None
    try:
        return datetime.strptime(value[:19], "%Y-%m-%d %H:%M:%S")
    except ValueError:
        return None


def text(value, limit: int):
    if value is None:
        return None
    return str(value)[:limit]


def channel_of(value):
    return text(value, 32) or "UNKNOWN"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写入；缺省仅 dry-run")
    args = ap.parse_args()

    batches = json.loads((ROOT / "signRecord_all.json").read_text(encoding="utf-8"))["data"]["data"]
    members = json.loads((ROOT / "signRecord_members.json").read_text(encoding="utf-8"))
    out_times = json.loads(
        (ROOT / "signRecord_out_insure_time.json").read_text(encoding="utf-8")
    )["values"]
    batches = [x["main"] for x in batches]

    base = 750_000_000_000_000_000
    batch_rows, item_rows, track_rows = [], [], []
    case_rows, report_rows, stakeholder_rows, claim_item_rows, case_track_rows = [], [], [], [], []
    seq = 0
    for m in batches:
        seq += 1
        bid = str(m["id"])
        bkey = f"pk-tpa-sign-{bid}"
        batch_rows.append((
            base + seq, 0, text(m.get("batchNo") or bid, 64), "OFFLINE", "MANUAL",
            channel_of(m.get("signChannel")), text(m.get("signStatus") or "WAIT_FOR_PARTICIPANT", 32),
            bkey, int(m.get("claimCount") or 0), 1 if m.get("imageUploadFlag") else 0,
            text(m.get("expressCompany"), 64), text(m.get("expressNo"), 64),
            text(m.get("sender"), 64), dt(m.get("arriveTime")),
            text(m.get("signInstitution"), 128), text(m.get("signOperator"), 64),
            dt(m.get("signTime")), text(m.get("remark"), 256),
            0, 0, dt(m.get("createTime")) or datetime.now(), datetime.now(), 0,
        ))
        track_rows.append((
            base + 100_000_000 + seq, 0, base + seq, None,
            text(m.get("signStatus") or "WAIT_FOR_PARTICIPANT", 32), "IMPORT_PK_TPA",
            f"TPA 团险签收批次导入（批次号 {m.get('batchNo') or bid}，源站赔案数 {m.get('claimCount') or 0}）",
            "从 tpa-test.pukangbao.com/jobManage/groupInsuranceSignList 抓取",
            "admin", dt(m.get("createTime")) or datetime.now(),
        ))
        detail = members.get(bid)
        if not detail:
            continue
        for mm in detail["members"]:
            cid = str(mm.get("id") or "")
            if not cid.isdigit():
                continue
            claim_no = text(mm.get("claimNo") or cid, 64)
            real_time = dt(out_times.get(claim_no))
            created_at = dt(mm.get("createTime")) or datetime.now()
            out_time = real_time or created_at
            fallback = "" if real_time else "；出险时间源站为空，按源站报案创建时间兜底"
            item_key = f"pk-tpa-sign-{bid}-{claim_no}"[:128]
            seq += 1
            item_rows.append([
                base + seq, 0, base + (batch_rows[-1][0] - base), None, claim_no, 0,
                0, created_at, created_at, 0, 0,
                text(mm.get("policyNo"), 64), 0,
                text(mm.get("outInsureName"), 128), text(mm.get("outInsureIdentityNo"), 64),
                out_time, "OUTPATIENT", "TPA_PUSH", item_key, "PENDING", None,
            ])
            case_rows.append({
                "seq": seq,
                "claim_no": claim_no,
                "case_id": base + 200_000_000 + seq,
                "report_id": base + 300_000_000 + seq,
                "stakeholder_id": base + 400_000_000 + seq,
                "claim_item_id": base + 500_000_000 + seq,
                "sign_track_id": base + 600_000_000 + seq,
                "case_track_id": base + 700_000_000 + seq,
                "policy_no": text(mm.get("policyNo"), 64),
                "insured_name": text(mm.get("outInsureName"), 128),
                "insured_id_no": text(mm.get("outInsureIdentityNo"), 64),
                "out_time": out_time,
                "created_at": created_at,
                "batch_no": text(mm.get("batchNo") or bid, 64),
                "fallback": fallback,
            })

    print(
        f"PLAN batches={len(batch_rows)} items={len(item_rows)} "
        f"tracks={len(track_rows)} candidate_cases={len(case_rows)}"
    )
    if not args.apply:
        print("DRY-RUN only; add --apply to write local dev DB")
        return

    env = load_env()
    conn = pymysql.connect(
        host=env["BONE_DB_HOST"], port=int(env["BONE_DB_PORT"]),
        user=env["BONE_DB_USERNAME"], password=env["BONE_DB_PASSWORD"],
        database=env["BONE_DB_NAME"], charset="utf8mb4", autocommit=False,
    )
    cur = conn.cursor()
    created = kept_new = skipped = manual = 0
    try:
        cur.execute(
            "SELECT business_key FROM t_claim_sign_batch "
            "WHERE business_key LIKE 'pk-tpa-sign-%'"
        )
        existing_batches = {r[0] for r in cur.fetchall()}
        new_batches = [r for r in batch_rows if r[7] not in existing_batches]
        new_batch_ids = {r[0] for r in new_batches}
        new_tracks = [r for r in track_rows if r[2] in new_batch_ids]
        print(
            f"SKIP_EXISTING_BATCHES={len(existing_batches)} NEW_BATCHES={len(new_batches)}"
        )
        if new_batches:
            cur.executemany("""INSERT INTO t_claim_sign_batch
                (id,tenant_id,batch_no,source,sign_type,sign_channel,sign_status,business_key,
                 claim_count,image_ready_count,express_company,express_no,sender,arrive_time,
                 sign_institution,sign_operator,sign_time,sign_remark,
                 version,created_by,created_at,updated_at,deleted)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", new_batches)
        if new_tracks:
            cur.executemany("""INSERT INTO t_claim_sign_track
                (id,tenant_id,batch_id,claim_id,sign_status,action_type,message,remark,operator,occurred_at)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", new_tracks)

        cur.execute("SELECT claim_no, id FROM t_claim_case WHERE tenant_id = 0")
        case_by_no = {r[0]: r[1] for r in cur.fetchall()}
        cur.execute(
            "SELECT id, case_status, created_case_id FROM t_claim_sign_batch_item "
            "WHERE tenant_id = 0"
        )
        item_state = {r[0]: (r[1], r[2]) for r in cur.fetchall()}
        cur.execute("SELECT id FROM t_claim_report WHERE tenant_id = 0")
        report_ids = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT claim_id FROM t_claim_stakeholder WHERE role = 'INSURED'")
        stakeholder_claims = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT id FROM t_claim_item WHERE tenant_id = 0")
        item_ids = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT id FROM t_claim_sign_track WHERE action_type LIKE 'AUTO_CASE%'")
        auto_track_ids = {r[0] for r in cur.fetchall()}
        cur.execute("SELECT id FROM t_claim_case_track WHERE action_code = 'AUTO_SIGN_BACKFILL'")
        backfill_track_ids = {r[0] for r in cur.fetchall()}

        report_sql = """INSERT INTO t_claim_report
            (id, report_no, policy_no, plan_id, insured_id_hash, claim_type, out_insure_time,
             plan_version, source_channel, source_system, status, tenant_id, idempotency_key,
             loss_description, case_count, created_by, created_at, updated_by, updated_at,
             version, deleted)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
        case_sql = """INSERT INTO t_claim_case
            (id, claim_no, report_id, report_no, idempotency_key, policy_no, plan_id,
             insured_id_hash, claim_type, status, status_sub, stage, invoice_code, invoice_no,
             out_insure_time, source_channel, source_system, batch_no, claim_amount,
             plan_version, tenant_id, hospital_code, diagnosis_code, invoice_date,
             created_by, created_at, updated_by, updated_at, version, deleted)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,
                    %s,%s,%s,%s,%s)"""
        stakeholder_sql = """INSERT INTO t_claim_stakeholder
            (id, claim_id, role, person_id_hash, name_masked, relation, phone_masked,
             collect_type, bank_ref, tenant_id, created_by, created_at, updated_by, updated_at,
             version, deleted)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
        claim_item_sql = """INSERT INTO t_claim_item
            (id, claim_id, item_name, item_code, category, quantity, unit_price, amount,
             medical_type, valid_amount, tenant_id, created_at, updated_at, version, deleted)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
        case_track_sql = """INSERT INTO t_claim_case_track
            (id, claim_id, from_status, to_status, stage, action_code, operator_id,
             operator_name, remark, tenant_id, created_by, created_at, updated_by, updated_at,
             deleted)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""

        for item, plan in zip(item_rows, case_rows):
            claim_no = plan["claim_no"]
            existing_case = case_by_no.get(claim_no)
            if existing_case:
                case_id = existing_case
                prior_status, prior_case_id = item_state.get(item[0], (None, None))
                backfill_created = 750_000_000_200_000_000 <= existing_case < 750_000_000_300_000_000
                if backfill_created or (prior_status == "NEW" and prior_case_id == existing_case):
                    item_status = "NEW"
                    kept_new += 1
                    action = "AUTO_CASE_CREATED"
                    message = "签收回填建案结果保持不变（幂等重跑）"
                else:
                    item_status = "SKIPPED"
                    skipped += 1
                    action = "AUTO_CASE_SKIPPED"
                    message = "明细引用本地已有案件，跳过建案"
            elif not plan["insured_id_no"] or not plan["policy_no"] or not plan["out_time"]:
                case_id = None
                item_status = "AWAIT_MANUAL"
                manual += 1
                action = "AUTO_CASE_MANUAL"
                message = "证件号或保单号缺失，待人工补录"
            else:
                case_id = plan["case_id"]
                item_status = "NEW"
                created += 1
                action = "AUTO_CASE_CREATED"
                message = "签收回填自动建案并推进至自动初审中" + plan["fallback"]
                report_no = f"RP-TPA-{plan['report_id']}"
                if plan["report_id"] not in report_ids:
                    cur.execute(report_sql, (
                        plan["report_id"], report_no, plan["policy_no"], 0,
                        plan["insured_id_no"], "OUTPATIENT", plan["out_time"], "",
                        "TPA_PUSH", "pk_tpa", "REPORTED", 0,
                        f"pk-tpa-sign-report-{claim_no}"[:64], "", 1,
                        1, plan["created_at"], 1, plan["created_at"], 0, 0,
                    ))
                    report_ids.add(plan["report_id"])
                cur.execute(case_sql, (
                    case_id, claim_no, plan["report_id"], report_no,
                    f"pk-tpa-sign-{claim_no}"[:64], plan["policy_no"], 0,
                    plan["insured_id_no"], "OUTPATIENT", "AUTO_PRE_EXAMINING", "1:YCBX",
                    "PRE_EXAM", None, None, plan["out_time"], "TPA_PUSH", "pk_tpa",
                    plan["batch_no"], 0, "", 0, "", "", None,
                    1, plan["created_at"], 1, plan["created_at"], 0, 0,
                ))
                if case_id not in stakeholder_claims:
                    cur.execute(stakeholder_sql, (
                        plan["stakeholder_id"], case_id, "INSURED",
                        plan["insured_id_no"], plan["insured_name"] or "", "", "", "",
                        "", 0, 1, plan["created_at"], 1, plan["created_at"], 0, 0,
                    ))
                    stakeholder_claims.add(case_id)
                if plan["claim_item_id"] not in item_ids:
                    cur.execute(claim_item_sql, (
                        plan["claim_item_id"], case_id, "签收导入占位费用", "SIGN-IMPORT",
                        "SERVICE", 1, 0, 0, "", 0, 0,
                        plan["created_at"], plan["created_at"], 0, 0,
                    ))
                    item_ids.add(plan["claim_item_id"])
                if plan["case_track_id"] not in backfill_track_ids:
                    cur.execute(case_track_sql, (
                        plan["case_track_id"], case_id, "DRAFT", "AUTO_PRE_EXAMINING",
                        "PRE_EXAM", "AUTO_SIGN_BACKFILL", "SYSTEM", "", message, 0,
                        1, plan["created_at"], 1, plan["created_at"], 0,
                    ))
                    backfill_track_ids.add(plan["case_track_id"])
                case_by_no[claim_no] = case_id

            item[3] = case_id
            item[19] = item_status
            item[20] = case_id
            cur.execute("""UPDATE t_claim_sign_batch_item SET
                claim_id=%s, policy_no=%s, plan_id=%s, insured_name=%s, insured_id_no=%s,
                out_insure_time=%s, claim_type=%s, source_channel=%s, business_key=%s,
                case_status=%s, created_case_id=%s, updated_at=%s WHERE id=%s""", (
                    item[3], item[11], item[12], item[13], item[14], item[15],
                    item[16], item[17], item[18], item[19], item[20],
                    datetime.now(), item[0],
                ))
            if plan["sign_track_id"] not in auto_track_ids:
                cur.execute("""INSERT INTO t_claim_sign_track
                    (id,tenant_id,batch_id,claim_id,sign_status,action_type,message,remark,
                     operator,occurred_at)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", (
                        plan["sign_track_id"], 0, item[2], case_id, "FINISHED", action,
                        message, "TPA 历史批次回填", "SYSTEM", plan["created_at"],
                    ))
                auto_track_ids.add(plan["sign_track_id"])

        conn.commit()
        print(
            f"COMMITTED created={created} kept_new={kept_new} skipped={skipped} "
            f"manual={manual} new_batches={len(new_batches)}"
        )
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
