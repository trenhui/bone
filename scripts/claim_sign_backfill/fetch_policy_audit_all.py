#!/usr/bin/env python3
"""全量抓取 TPA「我的审核」案件 + 「团险保单管理」保单及其规则/人员配置（只读）。

用法：python3 scripts/claim_sign_backfill/fetch_policy_audit_all.py

接口来源（2026-11-28 真机抓包）：
  · /myJob/myAudit            → POST /data-api/tpa/query/list  modelNames=["claimDetail"], stage=AUDITING
  · /policyConfig/groupPolicyList → POST /data-api/tpa/query/list  modelNames=["policy"]
  · 保单「配置规则」页          → GET  /data-api/tpa/adjust/plan/queryPlanByPolicy
                                 GET  /data-api/tpa/adjust/plan/policyInfo
                                 POST /data-api/tpa/personalQuota/list
                                 POST /data-api/tpa/personalQuota/changeList
                                 GET  /data-api/tpa/personalQuota/getOperateBatchList
  · 案件详情/影像              → GET  /data-api/tpa/claim/detail, POST /data-api/tpa/image/query

纪律：只调用查询接口；不落 token；源站脏字段原样保留，不伪造缺失数据。
产物落 outputs/tpa-claim-crawl/（gitignored），全部带 SHA-256 清单。
"""

from __future__ import annotations

import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from pathlib import Path

TPA = "https://tpa-test.pukangbao.com"
ROOT = Path(__file__).resolve().parents[2] / "outputs" / "tpa-claim-crawl"
WORKERS = 5


def now_cn() -> str:
    return datetime.now(timezone(timedelta(hours=8))).strftime("%Y-%m-%d %H:%M:%S +0800")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class TpaClient:
    """data-api 只认 Sa-Token Cookie，Bearer 返回 401。"""

    def __init__(self) -> None:
        req = urllib.request.Request(
            TPA + "/admin-api/system/auth/login",
            data=json.dumps({"username": "admin", "password": "123456"}).encode(),
            headers={"Content-Type": "application/json", "tenant-id": "1"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=40) as resp:
            payload = json.loads(resp.read().decode())
        if not payload.get("success"):
            raise RuntimeError(f"TPA 登录失败: {payload}")
        self.cookie = "Satoken=" + payload["data"]

    def _call(self, path: str, payload: dict | None, method: str) -> dict:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        req = urllib.request.Request(
            TPA + path,
            data=data,
            headers={
                "Cookie": self.cookie,
                "tenant-id": "1",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method=method,
        )
        last: Exception | None = None
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=60) as resp:
                    return json.loads(resp.read().decode("utf-8", "replace"))
            except Exception as exc:  # noqa: BLE001 - 网络抖动重试，错误最终记录到产物
                last = exc
                if attempt < 2:
                    time.sleep(1 + attempt)
        raise RuntimeError(f"{method} {path} 失败: {last}")

    def post(self, path: str, payload: dict) -> dict:
        return self._call(path, payload, "POST")

    def get(self, path: str) -> dict:
        return self._call(path, None, "GET")


def ok(payload: dict, label: str) -> dict:
    if not payload.get("success"):
        raise RuntimeError(f"{label}: code={payload.get('code')} msg={payload.get('message')}")
    return payload["data"]


def query_list(client: TpaClient, model: str, params: list[dict], size: int = 1000) -> dict:
    return ok(
        client.post(
            "/data-api/tpa/query/list",
            {
                "queryParams": params,
                "modelNames": [model],
                "pageNo": 1,
                "pageSize": size,
            },
        ),
        f"query/list {model}",
    )


def write_json(name: str, obj: object) -> str:
    raw = json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8")
    (ROOT / name).write_bytes(raw)
    return sha(raw)


def fetch_policy_config(client: TpaClient, policy_no: str) -> tuple[str, dict]:
    """单个保单的规则 + 人员配置；失败逐项记录，不中断整批。"""
    out: dict = {"policyNo": policy_no, "fetchedAt": now_cn()}
    # 中文保单号必须百分号编码：urllib 对非 ASCII URL 会抛 UnicodeEncodeError。
    quoted = urllib.parse.quote(policy_no, safe="")
    calls = {
        "policyInfo": ("GET", f"/data-api/tpa/adjust/plan/policyInfo?policyNo={quoted}", None),
        "plan": ("GET", f"/data-api/tpa/adjust/plan/queryPlanByPolicy?policyNo={quoted}", None),
        "personalQuota": (
            "POST",
            "/data-api/tpa/personalQuota/list",
            {"pageNo": 1, "pageSize": 500, "policyNo": policy_no},
        ),
        "quotaChanges": (
            "POST",
            "/data-api/tpa/personalQuota/changeList",
            {
                "pageNo": 1,
                "pageSize": 500,
                "policyNo": policy_no,
                "insuredName": "",
                "insuredCertificateType": "",
                "insuredCertificateNumber": "",
            },
        ),
        "operateBatches": (
            "GET",
            f"/data-api/tpa/personalQuota/getOperateBatchList?policyNo={quoted}&pageNo=1&pageSize=500",
            None,
        ),
    }
    for key, (method, path, payload) in calls.items():
        try:
            resp = client.get(path) if method == "GET" else client.post(path, payload)
            out[key] = resp.get("data", resp)
        except Exception as exc:  # noqa: BLE001
            out[key] = None
            out.setdefault("errors", {})[key] = str(exc)[:300]
    return policy_no, out


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    client = TpaClient()
    print("LOGIN_OK", now_cn(), flush=True)

    # 1) 团险保单全量
    policies = query_list(client, "policy", [], size=1000)
    policy_rows = policies["data"]
    if len(policy_rows) != policies["totalCount"]:
        raise RuntimeError(f"保单抓取不完整 total={policies['totalCount']} fetched={len(policy_rows)}")
    print("POLICY_TOTAL", policies["totalCount"], flush=True)

    # 2) 我的审核案件（stage=AUDITING）
    audit = query_list(
        client,
        "claimDetail",
        [{"field": "stage", "value": "AUDITING", "type": "EQUAL"}],
        size=500,
    )
    audit_rows = audit["data"]
    if len(audit_rows) != audit["totalCount"]:
        raise RuntimeError(f"审核案件抓取不完整 total={audit['totalCount']} fetched={len(audit_rows)}")
    print("AUDIT_TOTAL", audit["totalCount"], flush=True)

    # 3) 案件全集 = 审核列表 + 既有签收明细（避免重复抓取）
    claim_numbers = {
        str((row.get("main") or {}).get("claimNo"))
        for row in audit_rows
        if (row.get("main") or {}).get("claimNo")
    }
    members_path = ROOT / "signRecord_members.json"
    if members_path.exists():
        members = json.loads(members_path.read_text(encoding="utf-8"))
        for item in members.values():
            for c in item.get("members") or []:
                if c and c.get("claimNo"):
                    claim_numbers.add(str(c["claimNo"]))
    claim_numbers = sorted(claim_numbers)
    print("CLAIM_UNIQUE", len(claim_numbers), flush=True)

    case_details: dict[str, object] = {}
    case_images: dict[str, object] = {}
    for idx, claim_no in enumerate(claim_numbers, 1):
        try:
            case_details[claim_no] = ok(client.get(f"/data-api/tpa/claim/detail?id={claim_no}"), "claim/detail")
        except Exception as exc:  # noqa: BLE001
            case_details[claim_no] = {"error": str(exc)[:300]}
        try:
            case_images[claim_no] = ok(
                client.post("/data-api/tpa/image/query", {"id": claim_no, "tenantId": "1", "pageNo": 0, "pageSize": 0}),
                "image/query",
            )
        except Exception as exc:  # noqa: BLE001
            case_images[claim_no] = {"error": str(exc)[:300]}
        if idx % 10 == 0 or idx == len(claim_numbers):
            print("CLAIM_PROGRESS", idx, "/", len(claim_numbers), flush=True)

    # 4) 每张保单的配置规则 + 人员配置（并发，逐项失败留痕）
    configs: dict[str, dict] = {}
    policy_nos = [str((row.get("main") or {}).get("policyNo")) for row in policy_rows]
    policy_nos = sorted({p for p in policy_nos if p and p != "None"})
    duplicate_policy_rows = len(policy_rows) - len(policy_nos)
    done = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(fetch_policy_config, client, no) for no in policy_nos]
        for fut in as_completed(futures):
            no, data = fut.result()
            configs[no] = data
            done += 1
            if done % 50 == 0 or done == len(policy_nos):
                print("POLICY_CONFIG_PROGRESS", done, "/", len(policy_nos), flush=True)

    # 5) 落盘 + 哈希清单
    hashes = {
        "policy_all.json": write_json("policy_all.json", {"success": True, "data": policies}),
        "my_audit_cases.json": write_json("my_audit_cases.json", {"success": True, "data": audit}),
        "case_details_all.json": write_json("case_details_all.json", case_details),
        "case_images_all.json": write_json("case_images_all.json", case_images),
        "policy_config_all.json": write_json("policy_config_all.json", configs),
    }
    quota_nonempty = sum(
        1
        for c in configs.values()
        if isinstance(c.get("personalQuota"), dict) and c["personalQuota"].get("totalCount", 0) > 0
    )
    plan_nonempty = sum(1 for c in configs.values() if c.get("plan"))
    meta = {
        "fetchedAt": now_cn(),
        "source": {
            "audit": TPA + "/myJob/myAudit",
            "policy": TPA + "/policyConfig/groupPolicyList",
        },
        "policyTotal": policies["totalCount"],
        "policyFetched": len(policy_rows),
        "policyUniqueNo": len(policy_nos),
        "duplicatePolicyRows": duplicate_policy_rows,
        "auditTotal": audit["totalCount"],
        "auditFetched": len(audit_rows),
        "claimUnique": len(claim_numbers),
        "policyWithPlan": plan_nonempty,
        "policyWithPersonalQuota": quota_nonempty,
        "policyConfigErrors": sum(len(c.get("errors") or {}) for c in configs.values()),
        "sha256": hashes,
    }
    write_json("fetch_policy_audit_meta.json", meta)
    print("FETCH_ALL_OK", json.dumps(meta, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print("FATAL", repr(exc), file=sys.stderr)
        raise
