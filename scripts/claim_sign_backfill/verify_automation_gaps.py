#!/usr/bin/env python3
"""签收自动化四项缺口的可重跑验证：金额透传 / 初审任务 / 阶段自洽 / 跨案累计账。

用法：python3 scripts/claim_sign_backfill/verify_automation_gaps.py
判据全部落在 DB 与 API 事实；任一失败直接非零退出。产物写到 /tmp/sign-automation-gaps-result.json。
"""
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

import pymysql

BASE = "http://127.0.0.1:18888"
OUT = Path("/tmp/sign-automation-gaps-result.json")
STAMP = str(int(time.time()))[-9:]
ROOT = Path(__file__).resolve().parents[2]

CHECKS = []
OBSERVATIONS = []


def env_db():
    env = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    return env


CONN = pymysql.connect(
    host=env_db()["BONE_DB_HOST"], port=int(env_db()["BONE_DB_PORT"]),
    user=env_db()["BONE_DB_USERNAME"], password=env_db()["BONE_DB_PASSWORD"],
    database=env_db()["BONE_DB_NAME"], charset="utf8mb4", autocommit=True)


def db1(sql, args=()):
    with CONN.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchone()


def call(method, path, token=None, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    hdr = dict(headers or {})
    if token:
        hdr["Authorization"] = "Bearer " + token
    if data is not None:
        hdr["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=hdr, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_raw": raw[:400]}


def check(name, cond, detail=""):
    CHECKS.append({"name": name, "pass": bool(cond), "detail": str(detail)[:400]})
    print(("PASS " if cond else "FAIL ") + name + (" | " + str(detail)[:200] if detail else ""))
    if not cond:
        raise AssertionError(name + ": " + str(detail)[:300])


def observe(name, detail):
    OBSERVATIONS.append({"name": name, "detail": str(detail)[:400]})
    print("OBS  " + name + " | " + str(detail)[:200])


def id_number(prefix17):
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    return prefix17 + "10X98765432"[
        sum(int(c) * w for c, w in zip(prefix17, weights)) % 11]


def wait_case(batch_id):
    for _ in range(30):
        row = db1(
            "SELECT created_case_id FROM t_claim_sign_batch_item "
            "WHERE batch_id=%s AND created_case_id IS NOT NULL LIMIT 1", (batch_id,))
        if row and row[0]:
            return str(row[0])
        time.sleep(0.5)
    return None


def main():
    code, login = call("POST", "/api/v1/iam/login",
                       body={"username": "admin", "password": "123456"})
    check("登录", code == 200, code)
    token = login["data"]["token"]

    # ---- 缺口 1/2：金额透传 + 自动初审任务 ----
    amount = 1888
    code, signed = call("POST", "/api/v1/claims/sign", token, {
        "businessKey": "GAP-BIZ-" + STAMP,
        "signChannel": "API_PUSH",
        "claimAmount": amount,
        "caseElements": [{
            "claimNo": "GAP-CLAIM-" + STAMP,
            "policyNo": "E2E-POLICY-0001",
            "planId": 9001,
            "insuredName": "缺口验证" + STAMP[-4:],
            "insuredIdNo": id_number("11010119900101002"),
            "outInsureTime": "2026-01-09T00:00:00Z",
            "claimType": "OUTPATIENT",
            "sourceChannel": "GAP_VERIFY",
        }],
    }, {"Idempotency-Key": "gap-sign-" + STAMP})
    check("自动签收", code == 200 and signed["data"]["created"], signed.get("data"))
    batch_id = str(signed["data"]["batchId"])
    case_id = wait_case(batch_id)
    check("自动建案", bool(case_id), {"batchId": batch_id, "caseId": case_id})

    sign_amount = db1(
        "SELECT claim_amount FROM t_claim_sign_batch_item WHERE batch_id=%s", (batch_id,))
    check("回执金额落签收明细", sign_amount and float(sign_amount[0]) == amount, sign_amount)
    case = db1(
        "SELECT claim_amount,status,stage,policy_no,plan_id,insured_id_hash "
        "FROM t_claim_case WHERE id=%s", (case_id,))
    check("案件金额透传", case and float(case[0]) == amount, case)
    check("自动初审阶段自洽", case and case[1] == "AUTO_PRE_EXAMINING"
          and case[2] == "PRE_EXAM", (case[1], case[2]))
    task = db1(
        "SELECT task_type,status,claim_amount FROM t_claim_task "
        "WHERE claim_id=%s AND deleted=0 ORDER BY id DESC LIMIT 1", (case_id,))
    check("自动初审任务落库", task and task[0] == "INITIAL_REVIEW"
          and float(task[2]) == amount, task)

    # ---- 缺口 3：历史回填案阶段修正 ----
    bad = db1(
        "SELECT COUNT(*) FROM t_claim_case "
        "WHERE status='AUTO_PRE_EXAMINING' AND stage='REVIEW'")
    check("无非法 REVIEW 阶段残留", bad and bad[0] == 0, bad)

    # ---- 缺口 4：跨案累计账可读 + 资金占额 ----
    src = db1(
        "SELECT file_ref,content_hash FROM t_claim_evidence "
        "WHERE claim_id=750000000200000029 AND doc_type='INVOICE' AND deleted=0 LIMIT 1")
    check("存在可复用影像", src and src[0] and src[1], src)
    code, ev = call("POST", f"/api/v1/claims/{case_id}/evidences", token, {
        "fileRef": src[0], "contentHash": src[1], "docType": "INVOICE",
        "verifyValid": "UNKNOWN"}, {"Idempotency-Key": "gap-ev-" + STAMP})
    check("登记材料", code in (200, 201), ev.get("message"))
    code, locked = call("POST", f"/api/v1/claims/{case_id}/evidences/lock", token)
    check("锁定证据集", code == 200, locked.get("message"))

    code, adj = call("POST", f"/api/v1/claims/{case_id}/adjust", token)
    check("执行理算", code == 200 and adj.get("data", {}).get("calculationId"), adj.get("message"))
    check("理算金额非零", float(adj["data"]["totalAmount"]) > 0,
          adj["data"]["totalAmount"])
    liability_uuid = adj["data"]["liabilityUuid"]

    code, quota_view = call("GET", f"/api/v1/claims/{case_id}/quota", token)
    check("读取额度年度", code == 200 and quota_view["data"]["quotaYear"] > 0,
          quota_view.get("message"))
    year = quota_view["data"]["quotaYear"]
    for quota_type, liability in (("PERSONAL", ""), ("LIABILITY_CAP", liability_uuid)):
        code, opened = call("POST", "/api/v1/claim-config/quota-accounts", token, {
            "insuredIdHash": case[5], "policyNo": case[3], "planId": case[4],
            "quotaYear": year, "quotaType": quota_type, "liabilityUuid": liability,
            "quotaInitial": 100000, "remark": "自动签收缺口验证开户",
        })
        check(f"开户 {quota_type}", code in (200, 201), opened.get("message"))

    code, latest = call("GET", f"/api/v1/claims/{case_id}/adjust", token)
    check("跨案累计账可读", code == 200
          and latest["data"].get("accumulationAvailable") is True,
          {"code": code, "flag": (latest.get("data") or {}).get("accumulationAvailable")})
    code, settle = call("POST", f"/api/v1/claims/{case_id}/settlement", token,
                        {"attempt": 1, "accountType": "PERSONAL"})
    check("资金阶段可占额", code in (200, 201) and settle.get("data", {}).get("settlementNo"),
          settle.get("message"))
    observe("结算单", settle.get("data", {}).get("settlementNo"))
    observe("理算金额", adj["data"]["totalAmount"])

    summary = {
        "caseId": case_id, "batchId": batch_id, "amount": amount,
        "task": task, "adjustmentTotal": adj["data"]["totalAmount"],
        "quotaYear": year, "liabilityUuid": liability_uuid,
        "passed": sum(1 for c in CHECKS if c["pass"]),
        "failed": sum(1 for c in CHECKS if not c["pass"]),
        "checks": CHECKS, "observations": OBSERVATIONS,
    }
    OUT.write_text(json.dumps(summary, ensure_ascii=False, indent=2, default=str),
                   encoding="utf-8")
    print(f"RESULT {summary['passed']}/{len(CHECKS)} PASS -> {OUT}")


if __name__ == "__main__":
    main()
