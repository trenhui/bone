#!/usr/bin/env python3
"""案件签收全链路 E2E：线上自动签收 + 线下四态批次签收 + 自动建案结果查询。

判据：任一步非预期状态即判失败并打印响应体（不静默吞错）。
用法：
  python3 scripts/e2e/claim-secure-signing-chain.py
可选：SIGN_E2E_CASE_ID 指定线下名单引用的既有案件 ID。
"""
import json
import os
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:18888"
STAMP = str(int(time.time()))[-9:]
EXISTING_CASE_ID = os.environ.get("SIGN_E2E_CASE_ID", "750000000200000029")

# 端点路径集中声明；影响分析按这里的字面量做「端点 ↔ 用例」映射。
PATHS = {
    "login": "/api/v1/iam/login",
    "auto_sign": "/api/v1/claims/sign",
    "create_batch": "/api/v1/claims/sign/batches",
    "participants": "/api/v1/claims/sign/batches/{batchId}/participants",
    "image": "/api/v1/claims/sign/batches/{batchId}/image",
    "confirm": "/api/v1/claims/sign/batches/{batchId}/confirm",
    "list": "/api/v1/claims/sign/batches",
    "detail": "/api/v1/claims/sign/batches/{batchId}",
    "items": "/api/v1/claims/sign/batches/{batchId}/items",
    "tracks": "/api/v1/claims/sign/batches/{batchId}/tracks",
}


def req(token, method, path, body=None, headers=None, expect="2"):
    data = json.dumps(body).encode() if body is not None else None
    hdr = dict(headers or {})
    if token:
        hdr["Authorization"] = "Bearer " + token
    if data is not None:
        hdr["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE + path, data=data, headers=hdr, method=method)
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            code, raw = response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as error:
        code, raw = error.code, error.read().decode("utf-8", "ignore")
    try:
        parsed = json.loads(raw)
    except Exception:
        parsed = {"_raw": raw[:400]}
    ok = str(code).startswith(expect)
    print("  [%s] %s %s %s" % ("OK " if ok else "BAD", code, method, path))
    if not ok:
        print("        " + raw[:400].replace("\n", " "))
        raise AssertionError("%s %s -> %s" % (method, path, code))
    return code, parsed


def id_number(prefix17):
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    check = "10X98765432"
    return prefix17 + check[sum(int(c) * w for c, w in zip(prefix17, weights)) % 11]


def main():
    _, login = req(None, "POST", PATHS["login"],
                   {"username": "admin", "password": "123456"})
    token = login["data"]["token"]

    # 线上自动签收：携带建案要素，回执应生成新案件；同键重放必须跳过。
    idem = "sign-chain-auto-" + STAMP
    auto_body = {
        "businessKey": "SIGN-CHAIN-BIZ-" + STAMP,
        "signChannel": "API_PUSH",
        "caseElements": [{
            "claimNo": "SIGN-CHAIN-" + STAMP,
            "policyNo": "P-SIGN-CHAIN-" + STAMP,
            "planId": 0,
            "insuredName": "链路测试" + STAMP[-4:],
            "insuredIdNo": id_number("11010119900101001"),
            "outInsureTime": "2026-01-09T00:00:00Z",
            "claimType": "OUTPATIENT",
            "sourceChannel": "E2E",
        }],
    }
    _, auto = req(token, "POST", PATHS["auto_sign"], auto_body,
                  {"Idempotency-Key": idem})
    assert auto["data"]["created"] is True, auto
    batch_id = str(auto["data"]["batchId"])

    _, replay = req(token, "POST", PATHS["auto_sign"], auto_body,
                    {"Idempotency-Key": idem})
    assert replay["data"]["created"] is False, replay
    assert str(replay["data"]["batchId"]) == batch_id, replay

    _, detail = req(token, "GET", PATHS["detail"].format(batchId=batch_id))
    assert detail["data"]["signStatus"] == "FINISHED", detail
    assert detail["data"]["source"] == "ONLINE" and detail["data"]["signType"] == "AUTO", detail

    _, items = req(token, "GET", PATHS["items"].format(batchId=batch_id))
    assert len(items["data"]) == 1 and items["data"][0]["caseStatus"] == "NEW", items
    assert items["data"][0]["caseId"], items

    _, tracks = req(token, "GET", PATHS["tracks"].format(batchId=batch_id))
    actions = [x["actionType"] for x in tracks["data"]]
    assert "AUTO_SIGN" in actions and "AUTO_CASE_CREATED" in actions, actions

    # 线下四态：创建批次 → 导入名单 → 登记影像 → 二次确认。
    _, created = req(token, "POST", PATHS["create_batch"], {
        "businessKey": "sign-chain-offline-" + STAMP,
        "signChannel": "EXPRESS",
        "expressCompany": "E2E快递",
        "expressNo": "EX" + STAMP,
        "sender": "链路测试",
        "insuranceCompany": "链路测试保司",
    })
    offline_id = str(created["data"])

    _, d = req(token, "GET", PATHS["detail"].format(batchId=offline_id))
    assert d["data"]["signStatus"] == "WAIT_FOR_PARTICIPANT", d

    req(token, "POST", PATHS["participants"].format(batchId=offline_id),
        {"claimIds": [EXISTING_CASE_ID]})
    _, d = req(token, "GET", PATHS["detail"].format(batchId=offline_id))
    assert d["data"]["signStatus"] == "WAIT_FOR_IMAGE", d

    req(token, "POST", PATHS["image"].format(batchId=offline_id))
    _, d = req(token, "GET", PATHS["detail"].format(batchId=offline_id))
    assert d["data"]["signStatus"] == "WAIT_FOR_CONFIRMATION", d

    _, confirm = req(token, "POST", PATHS["confirm"].format(batchId=offline_id),
                     {"remark": "E2E 线下签收二次确认通过，资料齐全"},
                     {"Idempotency-Key": "sign-chain-confirm-" + STAMP})
    assert confirm["data"] is not None, confirm

    _, d = req(token, "GET", PATHS["detail"].format(batchId=offline_id))
    assert d["data"]["signStatus"] == "FINISHED", d

    _, off_tracks = req(token, "GET", PATHS["tracks"].format(batchId=offline_id))
    off_actions = [x["actionType"] for x in off_tracks["data"]]
    assert all(a in off_actions for a in
               ["CREATE", "IMPORT_PARTICIPANTS", "IMAGE_READY", "CONFIRM"]), off_actions

    # 终态重复登记应被状态机拒绝。
    req(token, "POST", PATHS["image"].format(batchId=offline_id), expect="4")

    _, listed = req(token, "GET", PATHS["list"] + "?page=1&size=20")
    assert listed["data"]["total"], listed
    print("E2E_CLAIM_SIGN_CHAIN_OK autoBatch=%s caseId=%s offlineBatch=%s" % (
        batch_id, items["data"][0]["caseId"], offline_id))


if __name__ == "__main__":
    main()
