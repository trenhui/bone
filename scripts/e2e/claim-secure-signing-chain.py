#!/usr/bin/env python3
"""案件签收全链路 E2E：线上自动签收 + 线下四态批次签收 + 自动建案结果查询。

为什么需要它
------------
`ClaimSignController` 的 8 个端点此前只有手动 curl 联调，没有纳入 E2E 用例登记，
影响分析门禁据此判定「本次变更涉及但零覆盖」。本脚本按真实用户路径串起闭环：
外部推送自动签收 → 创建线下批次 → 导入名单 → 影像登记 → 二次确认
→ 列表 / 详情 / 轨迹回读，并断言自动建案摘要可见。

判据：任一步非 2xx 即判失败并打印响应体（不静默吞错）。

用法：
  python3 scripts/e2e/claim-secure-signing-chain.py
"""
import json
import subprocess
import time

GW = "http://127.0.0.1:18888"
STAMP = str(int(time.time()))[-8:]

# 端点路径集中声明；影响分析按这里的字面量做「端点 ↔ 用例」映射。
PATHS = {
    "login": "/api/v1/auth/login",
    "auto_sign": "/api/v1/claims/sign",
    "create_batch": "/api/v1/claims/sign/batches",
    "participants": "/api/v1/claims/sign/batches/{batchId}/participants",
    "image": "/api/v1/claims/sign/batches/{batchId}/image",
    "confirm": "/api/v1/claims/sign/batches/{batchId}/confirm",
    "list": "/api/v1/claims/sign/batches",
    "detail": "/api/v1/claims/sign/batches/{batchId}",
    "tracks": "/api/v1/claims/sign/batches/{batchId}/tracks",
}


def curl(args, timeout=30):
    cmd = ["curl", "-s", "--noproxy", "*", "-m", str(timeout)] + args
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5)
        return p.stdout
    except subprocess.TimeoutExpired:
        return ""


def req(token, method, path, body=None, headers=None, expect="2"):
    args = ["-o", "/tmp/_claim_sign_body.txt", "-w", "%{http_code}"]
    if token:
        args += ["-H", "Authorization: Bearer " + token]
    for k, v in (headers or {}).items():
        args += ["-H", k + ": " + v]
    if body is not None:
        args += ["-H", "Content-Type: application/json", "-X", method, "-d", json.dumps(body)]
    else:
        args += ["-X", method]
    args.append(GW + path)
    code = curl(args).strip()
    try:
        raw = open("/tmp/_claim_sign_body.txt", encoding="utf-8", errors="ignore").read()
    except OSError:
        raw = ""
    ok = code.startswith(expect)
    print("  [%s] %s %s %s" % ("OK " if ok else "BAD", code, method, path))
    if not ok:
        print("        " + raw[:400].replace("\n", " "))
        raise AssertionError("%s %s -> %s" % (method, path, code))
    try:
        return code, json.loads(raw)
    except Exception:
        return code, {"_raw": raw}


def main():
    _, login = req(None, "POST", PATHS["login"],
                   {"username": "admin", "password": "123456"})
    token = login["data"]["token"]

    # 线上自动签收：携带建案要素，回执应生成新案件。
    idem = "sign-chain-auto-" + STAMP
    _, auto = req(token, "POST", PATHS["auto_sign"], {
        "businessKey": idem,
        "signChannel": "EXTERNAL_PUSH",
        "caseElements": [{
            "claimNo": "SIGN-CHAIN-" + STAMP,
            "policyNo": "P-SIGN-CHAIN-" + STAMP,
            "planId": 0,
            "insuredName": "链路测试",
            "insuredIdNo": "110101199001099999",
            "outInsureTime": "2026-01-09T00:00:00Z",
            "claimType": "OUTPATIENT",
            "sourceChannel": "E2E",
        }],
    }, {"Idempotency-Key": idem})

    _, created = req(token, "POST", PATHS["create_batch"], {
        "businessKey": "sign-chain-batch-" + STAMP,
        "signChannel": "OFFLINE",
        "source": "TPA",
        "remark": "E2E 线下签收批次",
    })
    batch_id = str(created["data"]["batchId"])

    req(token, "POST", PATHS["participants"].format(batchId=batch_id), {
        "items": [{"claimNo": "SIGN-CHAIN-ITEM-" + STAMP, "claimId": None}],
    })
    req(token, "POST", PATHS["image"].format(batchId=batch_id), {
        "imageReady": True,
        "remark": "E2E 影像齐备",
    })
    req(token, "POST", PATHS["confirm"].format(batchId=batch_id), {
        "remark": "E2E 二次确认签收通过",
    })

    _, listed = req(token, "GET", PATHS["list"] + "?page=1&size=20")
    _, detail = req(token, "GET", PATHS["detail"].format(batchId=batch_id))
    _, tracks = req(token, "GET", PATHS["tracks"].format(batchId=batch_id))
    summary = detail.get("data", {})
    assert "createdCaseCount" in summary, "详情缺少自动建案摘要"
    assert tracks.get("data"), "轨迹为空"
    print("E2E_CLAIM_SIGN_CHAIN_OK autoCreated=%s batch=%s tracks=%d" % (
        auto.get("data", {}).get("created"), batch_id, len(tracks["data"])))


if __name__ == "__main__":
    main()
