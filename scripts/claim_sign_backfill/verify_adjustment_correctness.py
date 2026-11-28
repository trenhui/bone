#!/usr/bin/env python3
"""独立复核自动签收案件的理算结果正确性（不采信引擎自报的「成功」）。

用法：python3 scripts/claim_sign_backfill/verify_adjustment_correctness.py

方法（业界最佳实践）：
  1. 等价类 + 边界值：多个受控金额（分、百、千、万、上限内大額）走完整自动签收→锁定→理算链路；
  2. 独立 oracle：从责任配置表读取 payPercent/免赔/限额，用 Python Decimal 按
     「基数 → 免赔 → 比例 → 控额」重算期望值，与 API/DB 结果逐分比对；
  3. 不变量自证：快照 base/ratioPayout/total/occupied 与归因行合计守恒；
  4. 幂等：同案重复读取理算结果，总额与 formulaHash 不变；
  5. 负向：负数金额必须被拒。

只写本地开发库受控案件；不碰生产。产物：outputs/tpa-claim-crawl/verify_adjustment_correctness_result.json
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "outputs" / "tpa-claim-crawl" / "verify_adjustment_correctness_result.json"
BASE = "http://127.0.0.1:18888"
# 签收 API 的 claimAmount 是 Long（整数元），故边界值取整数分界点。
AMOUNTS = ["1", "100", "1888", "9999", "100000"]
TWO = Decimal("0.01")


def env_db() -> dict:
    out = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


CONN = pymysql.connect(
    host=env_db()["BONE_DB_HOST"], port=int(env_db()["BONE_DB_PORT"]),
    user=env_db()["BONE_DB_USERNAME"], password=env_db()["BONE_DB_PASSWORD"],
    database=env_db()["BONE_DB_NAME"], charset="utf8mb4", autocommit=True)
CHECKS: list[dict] = []
CASES: list[dict] = []


def db1(sql, args=()):
    with CONN.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchone()


def call(method: str, path: str, token: str | None = None, body: dict | None = None, headers=None):
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    hdr = dict(headers or {})
    if token:
        hdr["Authorization"] = "Bearer " + token
    if data is not None:
        hdr["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=hdr, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8", "ignore"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "ignore")
        try:
            return exc.code, json.loads(raw)
        except Exception:  # noqa: BLE001
            return exc.code, {"_raw": raw[:300]}


def check(name: str, cond: bool, detail="") -> None:
    CHECKS.append({"name": name, "pass": bool(cond), "detail": str(detail)[:400]})
    print(("PASS " if cond else "FAIL ") + name + (" | " + str(detail)[:180] if detail else ""), flush=True)
    if not cond:
        raise AssertionError(f"{name}: {detail}")


def money(value) -> Decimal:
    return Decimal(str(value)).quantize(TWO, rounding=ROUND_HALF_UP)


def id_number(prefix17: str) -> str:
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    return prefix17 + "10X98765432"[sum(int(c) * w for c, w in zip(prefix17, weights)) % 11]


def wait_case(batch_id: str):
    for _ in range(60):
        row = db1("SELECT created_case_id FROM t_claim_sign_batch_item "
                  "WHERE batch_id=%s AND created_case_id IS NOT NULL LIMIT 1", (batch_id,))
        if row and row[0]:
            return str(row[0])
        time.sleep(0.4)
    return None


def liability_rule(uuid: str) -> dict:
    row = db1(
        "SELECT pay_percent,liability_deduct,invoice_deduct,adjust_deduct,liability_limit "
        "FROM t_adj_liability WHERE liability_uuid=%s AND deleted=0", (uuid,))
    if not row:
        raise RuntimeError("责任配置不存在: " + uuid)
    return {
        "payPercent": Decimal(str(row[0])),
        "deduct": Decimal(str(row[1])),
        "invoiceDeduct": Decimal(str(row[2])),
        "adjustDeduct": Decimal(str(row[3])),
        "limit": Decimal(str(row[4])),
    }


def independent_expected(amount: Decimal, rule: dict, snap: dict) -> Decimal:
    """从配置与快照独立重算，不调用被测引擎的任何代码。"""
    base = money(snap["base"])
    if base != amount:
        raise AssertionError(f"基数不等于回执金额: {base} != {amount}")
    # 发票级免赔 → 年度免赔 → 比例（本受控责任 deduct target=CALCULATED_AMOUNT）
    effective = max(Decimal("0"), base - money(rule["invoiceDeduct"]) - money(rule["deduct"]))
    ratio = money(effective * rule["payPercent"] / Decimal("100"))
    ratio = max(Decimal("0"), ratio - money(rule["adjustDeduct"]))
    # 控额：责任限额 - 已用；额度账可用也须满足（快照给出 limitUsedBefore）
    remaining = money(rule["limit"]) - money(snap.get("limitUsedBefore") or 0)
    return money(min(ratio, max(Decimal("0"), remaining)))


def verify_amount(token: str, amount: Decimal, idx: int) -> dict:
    stamp = str(int(time.time() * 1000))[-9:] + str(idx)
    ident = id_number("1101011990010100" + str(idx % 10))
    code, signed = call("POST", "/api/v1/claims/sign", token, {
        "businessKey": "ACCC-BIZ-" + stamp,
        "signChannel": "API_PUSH",
        "claimAmount": int(amount),
        "caseElements": [{
            "claimNo": "ACCC-CLAIM-" + stamp,
            "policyNo": "E2E-POLICY-0001",
            "planId": 9001,
            "insuredName": "理算复核" + stamp[-4:],
            "insuredIdNo": ident,
            "outInsureTime": "2026-01-09T00:00:00Z",
            "claimType": "OUTPATIENT",
            "sourceChannel": "ACCURACY_VERIFY",
        }],
    }, {"Idempotency-Key": "acc-sign-" + stamp})
    check(f"[{amount}] 自动签收", code == 200 and signed.get("data", {}).get("created"), (code, signed.get("message")))
    batch_id = str(signed["data"]["batchId"])
    case_id = wait_case(batch_id)
    check(f"[{amount}] 自动建案", bool(case_id), batch_id)

    src = db1("SELECT file_ref,content_hash FROM t_claim_evidence "
              "WHERE claim_id=750000000200000029 AND doc_type='INVOICE' AND deleted=0 LIMIT 1")
    check(f"[{amount}] 存在可复用影像", bool(src and src[0]), src)
    code, ev = call("POST", f"/api/v1/claims/{case_id}/evidences", token, {
        "fileRef": src[0], "contentHash": src[1], "docType": "INVOICE", "verifyValid": "UNKNOWN",
    }, {"Idempotency-Key": "acc-ev-" + stamp})
    check(f"[{amount}] 登记材料", code in (200, 201), ev.get("message"))
    code, locked = call("POST", f"/api/v1/claims/{case_id}/evidences/lock", token)
    check(f"[{amount}] 锁定证据", code == 200, locked.get("message"))

    code, adj = call("POST", f"/api/v1/claims/{case_id}/adjust", token)
    check(f"[{amount}] 执行理算", code == 200 and (adj.get("data") or {}).get("calculationId"), (code, adj.get("message")))
    data = adj["data"]
    calc_id = str(data["calculationId"])
    row = db1("SELECT total_amount,formula_hash,formula_snapshot,liability_uuid FROM t_adj_calculation WHERE id=%s", (calc_id,))
    check(f"[{amount}] 快照落库", bool(row), calc_id)
    snap = json.loads(row[2])
    rule = liability_rule(row[3])
    expected = independent_expected(amount, rule, snap)
    actual = money(data["totalAmount"])
    check(f"[{amount}] 独立重算金额一致", actual == expected, {"expected": str(expected), "actual": str(actual)})
    check(f"[{amount}] DB 总额一致", money(row[0]) == expected, row[0])
    check(f"[{amount}] 快照 total 一致", money(snap["total"]) == expected, snap.get("total"))
    check(f"[{amount}] 快照 ratioPayout 一致", money(snap["ratioPayout"]) == expected, snap.get("ratioPayout"))
    check(f"[{amount}] 快照 occupied 一致", money(snap["occupied"]) == expected, snap.get("occupied"))
    check(f"[{amount}] 快照无零赔付归因", not snap.get("zeroReason"), snap.get("zeroReason"))
    line_sum = sum((money(x["payout"]) for x in snap["lines"]), Decimal("0"))
    check(f"[{amount}] 归因明细合计守恒", line_sum == expected, str(line_sum))
    check(f"[{amount}] 基数等于回执金额", money(snap["base"]) == amount, snap.get("base"))

    # 幂等：重复执行理算应产出同一金额与同一快照哈希
    code, again = call("POST", f"/api/v1/claims/{case_id}/adjust", token)
    check(f"[{amount}] 重算幂等（金额）", code == 200 and money(again["data"]["totalAmount"]) == expected,
          again.get("data", {}).get("totalAmount"))
    code, fetched = call("GET", f"/api/v1/claims/{case_id}/adjust", token)
    check(f"[{amount}] 重算幂等（哈希）", code == 200 and fetched["data"].get("formulaHash") == data.get("formulaHash"),
          {"first": data.get("formulaHash"), "second": (fetched.get("data") or {}).get("formulaHash")})

    return {
        "amount": str(amount), "caseId": case_id, "calculationId": calc_id,
        "expected": str(expected), "actual": str(actual), "formulaHash": data.get("formulaHash"),
        "payPercent": str(rule["payPercent"]), "lineSum": str(line_sum),
    }


def main() -> None:
    code, login = call("POST", "/api/v1/iam/login", body={"username": "admin", "password": "123456"})
    check("登录", code == 200, code)
    token = login["data"]["token"]

    for idx, raw in enumerate(AMOUNTS, 1):
        CASES.append(verify_amount(token, Decimal(raw), idx))

    # 负向：负数金额应被拒绝，不能产出负赔付
    stamp = str(int(time.time() * 1000))[-9:]
    code, bad = call("POST", "/api/v1/claims/sign", token, {
        "businessKey": "ACCC-NEG-" + stamp, "signChannel": "API_PUSH", "claimAmount": -100,
        "caseElements": [{
            "claimNo": "ACCC-NEG-CLAIM-" + stamp, "policyNo": "E2E-POLICY-0001", "planId": 9001,
            "insuredName": "负向验证", "insuredIdNo": id_number("11010119900101009"),
            "outInsureTime": "2026-01-09T00:00:00Z", "claimType": "OUTPATIENT", "sourceChannel": "ACCURACY_VERIFY",
        }],
    }, {"Idempotency-Key": "acc-neg-" + stamp})
    check("负向：负数金额被拒绝", code >= 400, {"code": code, "body": bad})

    result = {
        "finishedAt": time.strftime("%Y-%m-%d %H:%M:%S +0800", time.localtime()),
        "oracle": "Python Decimal 独立重算：发票免赔→年度免赔→比例→责任控额，HALF_UP 两位小数",
        "passed": sum(1 for c in CHECKS if c["pass"]),
        "failed": sum(1 for c in CHECKS if not c["pass"]),
        "cases": CASES,
        "checks": CHECKS,
    }
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"RESULT {result['passed']}/{len(CHECKS)} PASS -> {OUT}", flush=True)


if __name__ == "__main__":
    main()
