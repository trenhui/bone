#!/usr/bin/env python3
"""真机全量抓取普康 TPA 团险签收列表 + 每批赔案明细（只读）。

用法：python3 scripts/claim_sign_backfill/fetch_sign_all.py

与历史 capture_pages.cjs 的区别：
  · 直接用 UI 登录拿到的 Sa-Token Cookie 调 data-api（Bearer 无效）；
  · 签收批次列表 pageSize=200 一次拉全，不再受页面默认 10 条限制；
  · 对每一批用 claimDetail 模型拉赔案明细（relatedId=批次号）；
  · 对每条赔案用 /data-api/tpa/claim/detail 拉真实出险时间；
  · 落盘产物覆盖 signRecord_all.json / signRecord_members.json /
    signRecord_out_insure_time.json，并写 fetch_sign_all_meta.json 记录总数与哈希。
"""

from __future__ import annotations

import hashlib
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

BASE = "https://tpa-test.pukangbao.com"
# 脚本入库路径为 scripts/claim_sign_backfill/，产物仍落 outputs/tpa-claim-crawl/。
ROOT = Path(__file__).resolve().parents[2] / "outputs" / "tpa-claim-crawl"


def now_cn() -> str:
    return datetime.now(timezone(timedelta(hours=8))).strftime("%Y-%m-%d %H:%M:%S +0800")


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class Client:
    """UI 登录拿 Sa-Token：data-api 只认 Cookie，Bearer 返回 401。"""

    def __init__(self) -> None:
        req = urllib.request.Request(
            BASE + "/admin-api/system/auth/login",
            data=json.dumps({"username": "admin", "password": "123456"}).encode(),
            headers={"Content-Type": "application/json", "tenant-id": "1"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=40) as resp:
            payload = json.loads(resp.read().decode())
        if not payload.get("success"):
            raise RuntimeError(f"登录失败: {payload}")
        self.token = payload["data"]
        self.cookie = "Satoken=" + self.token

    def post(self, path: str, payload: dict) -> dict:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(
            BASE + path,
            data=data,
            headers={
                "Cookie": self.cookie,
                "tenant-id": "1",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8", "replace"))

    def get(self, path: str) -> dict:
        req = urllib.request.Request(
            BASE + path,
            headers={"Cookie": self.cookie, "tenant-id": "1", "Accept": "application/json"},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8", "replace"))


def ok(payload: dict, label: str) -> dict:
    if not payload.get("success"):
        raise RuntimeError(f"{label} 调用失败: code={payload.get('code')} msg={payload.get('message')}")
    return payload["data"]


def query_list(client: Client, model: str, field: str | None, value: str | None, size: int = 200) -> dict:
    params: list[dict] = []
    if field and value is not None:
        params.append({"field": field, "value": value, "type": "EQUAL"})
    payload = {
        "bizIdentityCode": None,
        "tenantId": "1",
        "queryParams": params,
        "modelNames": [model],
        "sortingFields": [],
        "pageNo": 1,
        "pageSize": size,
    }
    return ok(client.post("/data-api/tpa/query/list", payload), f"query/list {model}")


def main() -> None:
    client = Client()
    print("LOGIN_OK", now_cn())

    # 1) 全量签收批次
    batch_data = query_list(client, "signRecord", None, None, size=500)
    batches = batch_data["data"]
    print("BATCH_TOTAL", batch_data["totalCount"], "FETCHED", len(batches))
    if len(batches) < batch_data["totalCount"]:
        raise RuntimeError(
            f"批次抓取不完整: total={batch_data['totalCount']} fetched={len(batches)}"
        )

    # 2) 每批赔案明细（relatedId=批次号）
    members: dict[str, dict] = {}
    for idx, row in enumerate(batches, 1):
        main_row = row.get("main") or {}
        bid = str(main_row.get("id"))
        detail = query_list(client, "claimDetail", "relatedId", bid, size=200)
        claim_rows = detail["data"]
        # 键名 members 与既有 import_sign_batches.py 契约一致。
        members[bid] = {"batch": main_row, "members": [x.get("main") for x in claim_rows]}
        print(f"DETAIL {idx}/{len(batches)} batch={bid} claims={len(claim_rows)}")

    # 3) 每条赔案真实出险时间（源站详情接口，缺失保持 null）
    out_times: dict[str, str | None] = {}
    claim_numbers = sorted(
        {
            str(c.get("claimNo"))
            for member in members.values()
            for c in (member.get("members") or [])
            if c and c.get("claimNo")
        }
    )
    print("CLAIM_UNIQUE", len(claim_numbers))
    for idx, claim_no in enumerate(claim_numbers, 1):
        try:
            detail = ok(client.get(f"/data-api/tpa/claim/detail?id={claim_no}"), "claim/detail")
            info = (detail or {}).get("outInsureInfo") or {}
            out_times[claim_no] = info.get("outInsureTime")
        except Exception as exc:
            out_times[claim_no] = None
            print("DETAIL_ERR", claim_no, str(exc)[:100])
        if idx % 20 == 0:
            print("CLAIM_DETAIL_PROGRESS", idx, "/", len(claim_numbers))

    # 保持与历史产物一致的 {"success":true,"data":{totalCount,data:[...]}} 包装，
    # 兼容 import_sign_batches.py 等既有读取方。
    wrapped = {"success": True, "code": 0, "message": "ok", "data": batch_data}
    payload = json.dumps(wrapped, ensure_ascii=False, indent=2).encode()
    (ROOT / "signRecord_all.json").write_bytes(payload)
    (ROOT / "signRecord_members.json").write_text(
        json.dumps(members, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (ROOT / "signRecord_out_insure_time.json").write_text(
        json.dumps({"values": out_times}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    meta = {
        "fetchedAt": now_cn(),
        "source": BASE + "/jobManage/groupInsuranceSignList",
        "batchTotal": batch_data["totalCount"],
        "batchFetched": len(batches),
        "batchSha256": sha(payload),
        "detailBatches": len(members),
        "detailClaims": sum(len(m.get("members") or []) for m in members.values()),
        "claimUnique": len(claim_numbers),
        "claimWithOutInsureTime": sum(1 for v in out_times.values() if v),
    }
    (ROOT / "fetch_sign_all_meta.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("FETCH_ALL_OK", json.dumps(meta, ensure_ascii=False))


if __name__ == "__main__":
    main()
