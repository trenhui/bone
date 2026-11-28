#!/usr/bin/env python3
"""把 TPA 全量保单/计划/人员额度同步到本地智能理赔（只走正规 API，幂等可重跑）。

用法：
    python3 scripts/claim_sign_backfill/sync_policy_config_to_bone.py --dry-run
    python3 scripts/claim_sign_backfill/sync_policy_config_to_bone.py --apply

数据来源：outputs/tpa-claim-crawl/{policy_all,policy_config_all,case_details_all}.json
映射纪律：
  · 保单号/名称/保司/生效区间取源站原值；源站没有的字段留空，不伪造；
  · 计划 planLimit=-1（源站表示不限）映射为 0（Bone 语义同「不限」），在结果中留痕；
  · 人员配置证件号只算 SHA-256 假名化 ID，明文绝不落库；quotaYear 取源站记录创建年；
  · 已存在对象一律跳过，不覆盖本地数据；案件只核对不新建（建案走既有签收链路）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "outputs" / "tpa-claim-crawl"
BASE = "http://127.0.0.1:18888"
CN_TZ = timezone(timedelta(hours=8))
WORKERS = 4


def now_cn() -> str:
    return datetime.now(CN_TZ).strftime("%Y-%m-%d %H:%M:%S +0800")


def call(method: str, path: str, token: str | None = None, body: dict | None = None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", "replace")
        try:
            return exc.code, json.loads(raw)
        except Exception:  # noqa: BLE001
            return exc.code, {"_raw": raw[:400]}
    except Exception as exc:  # noqa: BLE001
        return 0, {"error": str(exc)[:300]}


def unwrap(payload: dict):
    return payload.get("data") if isinstance(payload, dict) else None


def as_list(value) -> list:
    """列表端点偶发返回错误包装（data 为对象），统一收敛成列表，避免 KeyError。"""
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ("records", "data", "list"):
            if isinstance(value.get(key), list):
                return value[key]
    return []


def instant(value: str | None) -> str | None:
    if not value:
        return None
    dt = datetime.strptime(value[:19], "%Y-%m-%d %H:%M:%S").replace(tzinfo=CN_TZ)
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def pseudonym(cert_no: str) -> str:
    """沿用既有 TPA 导入口径 sha256("pk_tpa:"+标识)；明文不进 Bone。"""
    return hashlib.sha256(("pk_tpa:" + cert_no).encode("utf-8")).hexdigest()


class Syncer:
    def __init__(self, token: str, apply: bool) -> None:
        self.token = token
        self.apply = apply
        self.stats: dict[str, dict[str, int]] = {
            name: {"CREATED": 0, "SKIPPED": 0, "FAILED": 0}
            for name in ("policy", "plan", "quota")
        }
        self.details: dict[str, list[dict]] = {"policy": [], "plan": [], "quota": []}
        self.plan_ids: dict[str, int] = {}

    def record(self, kind: str, status: str, key: str, note: str = "") -> None:
        self.stats[kind][status] += 1
        if status != "SKIPPED" or len(self.details[kind]) < 30:
            self.details[kind].append({"key": key, "status": status, "note": note[:200]})

    def exists_policy(self, policy_no: str) -> bool:
        code, payload = call("GET", "/api/v1/claim-config/policies/" + urllib.parse.quote(policy_no, safe=""), self.token)
        return code == 200 and unwrap(payload) is not None

    def sync_policy(self, row: dict) -> tuple[str, str]:
        main = row.get("main") or {}
        no = str(main.get("policyNo") or "")
        if not no:
            return no, "SKIPPED|保单号为空"
        start, end = instant(main.get("effdate")), instant(main.get("expdate"))
        if not start or not end:
            return no, "SKIPPED|源站缺生效区间"
        if start >= end:
            return no, "SKIPPED|源站生效起晚于生效止（脏数据，拒绝入库且不篡改）"
        if self.exists_policy(no):
            return no, "SKIPPED|已存在"
        body = {
            "policyNo": no,
            "policyName": main.get("insureName") or "",
            "insurerName": main.get("insuranceName") or "",
            "branchName": "",
            "insureName": main.get("insureName") or "",
            "bizIdentityCode": "",
            "productType": "",
            "policyType": "GROUP",
            "effectiveStart": start,
            "effectiveEnd": end,
            "waitingPeriod": 0,
            "policyVersion": "v1",
            "insuredSnapshot": "[]",
            "docClaimApply": "NEED",
            "docClaimNotice": "NEED",
            "insurerPolicyNo": main.get("externalPolicyNo") or "",
        }
        if not self.apply:
            return no, "CREATED|dry-run"
        code, payload = call("POST", "/api/v1/claim-config/policies", self.token, body)
        if code == 201:
            return no, "CREATED|OK"
        return no, f"FAILED|{code} {payload.get('message') or payload.get('error') or payload}"

    def existing_plan_codes(self, policy_no: str) -> set[str]:
        code, payload = call(
            "GET",
            "/api/v1/claim-config/plans?policyNo=" + urllib.parse.quote(policy_no, safe=""),
            self.token,
        )
        if code != 200:
            return set()
        return {str(x.get("planCode")) for x in as_list(unwrap(payload))}

    def sync_plan(self, policy_no: str, plan: dict) -> tuple[str, str]:
        code = str(plan.get("planCode") or plan.get("uuid") or "")
        if not code:
            return policy_no, "SKIPPED|计划编码为空"
        if self.exists_policy(policy_no):
            existing = self.existing_plan_codes(policy_no)
            if code in existing:
                return policy_no, "SKIPPED|计划已存在"
        limit = plan.get("planLimit")
        note = ""
        if limit is None:
            limit = 0
            note = "源站无额度→0(不限)"
        elif float(limit) < 0:
            limit = 0
            note = "源站 -1(不限)→0"
        body = {
            "policyNo": policy_no,
            "planCode": code,
            "planName": plan.get("planName") or "",
            "planLimit": str(limit),
            "waitingPeriod": 0,
            "versionNo": "v1",
        }
        if not self.apply:
            return policy_no, f"CREATED|dry-run {code}"
        status, payload = call("POST", "/api/v1/claim-config/plans", self.token, body)
        data = unwrap(payload) or {}
        pid = data.get("planId") or data.get("id")
        if status == 201 and pid:
            self.plan_ids[policy_no] = int(pid)
            return policy_no, f"CREATED|{code} planId={pid} {note}".strip()
        return policy_no, f"FAILED|{status} {payload.get('message') or payload}"

    def quota_exists(self, policy_no: str, hashed: str) -> bool:
        code, payload = call(
            "GET",
            "/api/v1/claim-config/quota-accounts/by-insured?policyNo="
            + urllib.parse.quote(policy_no, safe="")
            + "&insuredIdHash="
            + hashed,
            self.token,
        )
        return code == 200 and bool(unwrap(payload))

    def sync_quota(self, policy_no: str, item: dict) -> tuple[str, str]:
        cert = str(item.get("insuredCertificateNumber") or "")
        if not cert:
            return policy_no, "SKIPPED|源站证件号为空"
        hashed = pseudonym(cert)
        if self.quota_exists(policy_no, hashed):
            return policy_no, "SKIPPED|额度账户已存在"
        plan_id = self.plan_ids.get(policy_no)
        if not plan_id:
            code, payload = call(
                "GET",
                "/api/v1/claim-config/plans?policyNo=" + urllib.parse.quote(policy_no, safe=""),
                self.token,
            )
            plans = as_list(unwrap(payload))
            if plans:
                plan_id = int(plans[0].get("planId") or plans[0].get("id"))
        if not plan_id:
            return policy_no, "SKIPPED|无可用计划ID（需先同步计划）"
        created = str(item.get("createTime") or "")
        year = int(created[:4]) if created[:4].isdigit() else datetime.now(CN_TZ).year
        body = {
            "insuredIdHash": hashed,
            "policyNo": policy_no,
            "planId": plan_id,
            "quotaYear": year,
            "quotaType": "PERSONAL",
            "liabilityUuid": "",
            "quotaInitial": str(item.get("quotaInitial") or 0),
            "remark": "TPA 人员配置同步；证件号 SHA-256 假名化，未落明文",
        }
        if not self.apply:
            return policy_no, f"CREATED|dry-run {hashed[:12]}"
        status, payload = call("POST", "/api/v1/claim-config/quota-accounts", self.token, body)
        if status in (200, 201):
            return policy_no, f"CREATED|{hashed[:12]} year={year}"
        return policy_no, f"FAILED|{status} {payload.get('message') or payload}"

    def handle(self, kind: str, key: str, result: str) -> None:
        status, _, note = result.partition("|")
        self.record(kind, status, key, note)


def load_json(name: str):
    return json.loads((OUT / name).read_text(encoding="utf-8"))


def verify_cases() -> dict:
    """案件只核对不新建：建案属于签收链路，避免绕开领域规则。"""
    import pymysql

    env = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip()
    conn = pymysql.connect(
        host=env["BONE_DB_HOST"], port=int(env["BONE_DB_PORT"]),
        user=env["BONE_DB_USERNAME"], password=env["BONE_DB_PASSWORD"],
        database=env["BONE_DB_NAME"], charset="utf8mb4")
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT claim_no, id, status, stage FROM t_claim_case WHERE tenant_id = 0")
            rows = {str(r[0]): r for r in cur.fetchall()}
        expected = set(load_json("case_details_all.json").keys())
        present = sorted(expected & set(rows))
        missing = sorted(expected - set(rows))
        return {
            "expected": len(expected), "present": len(present), "missing": missing,
            "sample": [{"claimNo": c, "id": str(rows[c][1]), "status": rows[c][2], "stage": rows[c][3]} for c in present[:5]],
        }
    finally:
        conn.close()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写入本地开发库；缺省 dry-run")
    args = ap.parse_args()

    status, payload = call("POST", "/api/v1/iam/login", body={"username": "admin", "password": "123456"})
    if status != 200 or not unwrap(payload) or not unwrap(payload).get("token"):
        raise RuntimeError(f"登录失败 {status} {payload}")
    token = unwrap(payload)["token"]

    policies = load_json("policy_all.json")["data"]["data"]
    configs = load_json("policy_config_all.json")
    rows_by_no: dict[str, dict] = {}
    for row in policies:
        main_row = row.get("main") or {}
        no = str(main_row.get("policyNo") or "")
        if no and no not in rows_by_no:
            rows_by_no[no] = row  # 源站重复保单号取首行，全部字段仍来自源站

    syncer = Syncer(token, args.apply)
    print("MODE", "APPLY" if args.apply else "DRY_RUN", "POLICIES", len(rows_by_no), flush=True)

    # 保单并发创建（GET 存在性 → POST 创建，幂等）
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {pool.submit(syncer.sync_policy, row): no for no, row in rows_by_no.items()}
        for fut in as_completed(futures):
            no = futures[fut]
            syncer.handle("policy", no, fut.result()[1])
    print("POLICY_DONE", syncer.stats["policy"], flush=True)

    # 计划：每保单按源站 planCode 去重
    plan_jobs: list[tuple[str, dict]] = []
    for no, cfg in configs.items():
        seen = set()
        for entry in cfg.get("plan") or []:
            plan = entry.get("plan") or {}
            code = str(plan.get("planCode") or plan.get("uuid") or "")
            if code and code not in seen:
                seen.add(code)
                plan_jobs.append((no, plan))
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {pool.submit(syncer.sync_plan, no, plan): (no, plan) for no, plan in plan_jobs}
        for fut in as_completed(futures):
            no, plan = futures[fut]
            syncer.handle("plan", f"{no}/{plan.get('planCode')}", fut.result()[1])
    print("PLAN_DONE", syncer.stats["plan"], flush=True)

    # 人员配置（额度），逐条走 API
    quota_jobs = [
        (no, item)
        for no, cfg in configs.items()
        for item in ((cfg.get("personalQuota") or {}).get("data") or [])
    ]
    for no, item in quota_jobs:
        syncer.handle("quota", f"{no}/{item.get('insuredName')}", syncer.sync_quota(no, item)[1])
    print("QUOTA_DONE", syncer.stats["quota"], flush=True)

    cases = verify_cases()
    result = {
        "finishedAt": now_cn(),
        "mode": "apply" if args.apply else "dry-run",
        "source": "https://tpa-test.pukangbao.com/...",
        "stats": syncer.stats,
        "details": syncer.details,
        "cases": cases,
    }
    target = OUT / "sync_policy_config_result.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("RESULT", json.dumps(result["stats"], ensure_ascii=False), "CASES", cases["present"], "/", cases["expected"], flush=True)
    print("RESULT_FILE", target, flush=True)
    if any(s["FAILED"] for s in syncer.stats.values()):
        sys.exit(2)


if __name__ == "__main__":
    main()
