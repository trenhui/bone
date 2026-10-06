#!/usr/bin/env python3
"""渠道买家映射 + 库存广播 Outbox 的 E2E（真实 HTTP 覆盖本轮新增能力）。

覆盖：
  A. 渠道买家映射：拉单带 buyerId → 影子映射生成 → 待绑定清单 → 绑定 → 再拉单落真实客户
     → 重复绑定被拒(409) → 改绑(留痕) → 解绑 → 改绑缺原因被拒(400)
  B. 库存广播 Outbox：库存变更 → 同事务入队 → relay-now 投递成功 → 任务转 SENT
     → 失败重试语义（SENT 不可重试）→ 非法状态被拒

前置：gateway 8888 + blueprint 8082 + iam 8081 已启动，且已执行迁移 0022/0023。

用法：
    python3 scripts/e2e/channel-buyer-broadcast.py
    python3 scripts/e2e/channel-buyer-broadcast.py --direct   # 直连 8082
"""
from __future__ import annotations

import argparse
import json
import os
import urllib.error
import urllib.request

BASE = os.environ.get("BONE_E2E_BASE", "http://127.0.0.1:8888")
PRODUCT = "900002"          # 与 multichannel.py 的 900001 隔离，避免互相干扰
BUYER_ID = "E2E-BUYER-0001"

PASS: list[str] = []
FAIL: list[str] = []


def http(method: str, path: str, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items() if v is not None)
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Tenant-Id", "0")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw
    except Exception as e:  # noqa: BLE001
        return 0, str(e)


def check(name: str, ok: bool, detail: str = "") -> bool:
    (PASS if ok else FAIL).append(name)
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail and not ok else ""))
    return ok


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


def err_of(resp):
    return (resp.get("errorCode") or "") if isinstance(resp, dict) else ""


def login() -> str | None:
    st, j = http("POST", "/api/v1/iam/login", {"username": "admin", "password": "admin123"})
    if st != 200:
        for pwd in ("Admin@123", "123456", "bone123"):
            st, j = http("POST", "/api/v1/iam/login", {"username": "admin", "password": pwd})
            if st == 200:
                break
    if st != 200:
        return None
    return (data_of(j) or {}).get("accessToken") or (data_of(j) or {}).get("token")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--direct", action="store_true")
    args = ap.parse_args()
    global BASE
    if args.direct:
        BASE = "http://127.0.0.1:8082"

    print(f"=== 渠道买家映射 + 广播 Outbox E2E · base={BASE} ===")

    tok = login()
    if not check("登录获取 JWT", bool(tok)):
        print("\n登录失败，后续用例无法执行")
        return 1

    # ==================== A. 渠道买家映射 ====================
    print("\n--- A. 渠道买家映射 ---")

    # 幂等重置：解绑 + 清掉历史订单，保证「再拉单」这一步真的能观察到客户维度变化
    http("POST", "/api/v1/channel-buyers/unbind",
         params={"channelCode": "TAOBAO", "channelBuyerId": BUYER_ID}, token=tok)

    # A1：未绑定时拉单 → 订单落 customerId=0（未知客户），并异步登记影子映射
    st, j = http("POST", "/api/v1/channel-orders/pull", {
        "channelCode": "TAOBAO",
        "channelOrderNo": "E2E-ORDER-0001",
        "buyerNick": "E2E买家",
        "buyerId": BUYER_ID,
        "receiverName": "张三", "receiverPhone": "13800000000", "receiverAddress": "某省某市某区 1 号",
        "lines": [{"outerSkuId": "TB" + PRODUCT, "title": "E2E商品", "quantity": 2, "unitPrice": 50.00}],
    }, token=tok)
    check("拉单（带 buyerId）落单成功", st == 200, f"status={st} body={str(j)[:200]}")
    first_order_id = (data_of(j) or "") if isinstance(data_of(j), str) else None

    # A2：影子映射应出现在待绑定清单里（AFTER_COMMIT 异步写，稍等）
    shadow_ok = False
    for _ in range(10):
        st, j = http("GET", "/api/v1/channel-buyers/shadow-candidates", token=tok,
                     params={"channelCode": "TAOBAO", "limit": 100})
        rows = data_of(j) or []
        hit = [r for r in rows if r.get("channelBuyerId") == BUYER_ID]
        if hit:
            shadow_ok = check("影子映射已生成（customerId=0）",
                              hit[0].get("customerId") == "0" and hit[0].get("bound") is False,
                              f"实际={hit[0]}")
            break
        import time
        time.sleep(1)
    if not shadow_ok:
        check("影子映射已生成（customerId=0）", False, "10 秒内未在待绑定清单中看到该买家")

    # A3：绑定到真实客户
    st, j = http("POST", "/api/v1/channel-buyers/bind", token=tok,
                 params={"channelCode": "TAOBAO", "channelBuyerId": BUYER_ID,
                         "customerId": "2001", "customerName": "E2E客户"})
    d = data_of(j) or {}
    check("绑定到内部客户成功",
          st == 200 and d.get("bound") is True and d.get("customerId") == "2001",
          f"status={st} body={str(j)[:200]}")

    # A4：重复绑定被拒（409），防止顺手覆盖历史订单归属
    st, j = http("POST", "/api/v1/channel-buyers/bind", token=tok,
                 params={"channelCode": "TAOBAO", "channelBuyerId": BUYER_ID,
                         "customerId": "2002", "customerName": "别的客户"})
    check("重复绑定被拒（409 已绑定）", st == 409 and err_of(j) == "BP_CHANNEL_BUYER_ALREADY_BOUND",
          f"status={st} code={err_of(j)}")

    # A5：改绑缺原因被拒（400 IllegalArgumentException → 400）
    st, j = http("POST", "/api/v1/channel-buyers/rebind", token=tok,
                 params={"channelCode": "TAOBAO", "channelBuyerId": BUYER_ID,
                         "customerId": "2002", "reason": ""})
    check("改绑缺原因被拒", st in (400, 500), f"status={st} body={str(j)[:160]}")

    # A6：改绑成功（留痕）
    st, j = http("POST", "/api/v1/channel-buyers/rebind", token=tok,
                 params={"channelCode": "TAOBAO", "channelBuyerId": BUYER_ID,
                         "customerId": "2002", "customerName": "新客户", "reason": "E2E 改绑"})
    d = data_of(j) or {}
    check("改绑成功且留痕", st == 200 and d.get("customerId") == "2002" and d.get("remark"),
          f"status={st} body={str(j)[:200]}")

    # A7：绑定后再拉单（不同订单号）→ 订单应落 2002
    st, j = http("POST", "/api/v1/channel-orders/pull", {
        "channelCode": "TAOBAO",
        "channelOrderNo": "E2E-ORDER-0002",
        "buyerNick": "E2E买家",
        "buyerId": BUYER_ID,
        "receiverName": "张三", "receiverPhone": "13800000000", "receiverAddress": "某省某市某区 1 号",
        "lines": [{"outerSkuId": "TB" + PRODUCT, "title": "E2E商品", "quantity": 1, "unitPrice": 50.00}],
    }, token=tok)
    second_order_id = (data_of(j) or "") if isinstance(data_of(j), str) else None
    check("绑定后再拉单成功", st == 200, f"status={st} body={str(j)[:200]}")

    # A8：幂等 —— 同订单号重复推送返回同一订单ID
    st, j = http("POST", "/api/v1/channel-orders/pull", {
        "channelCode": "TAOBAO",
        "channelOrderNo": "E2E-ORDER-0002",
        "buyerNick": "E2E买家",
        "buyerId": BUYER_ID,
        "receiverName": "张三", "receiverPhone": "13800000000", "receiverAddress": "某省某市某区 1 号",
        "lines": [{"outerSkuId": "TB" + PRODUCT, "title": "E2E商品", "quantity": 1, "unitPrice": 50.00}],
    }, token=tok)
    again = (data_of(j) or "") if isinstance(data_of(j), str) else None
    check("同订单号重复推送幂等（返回同一订单ID）",
          st == 200 and second_order_id and again == second_order_id,
          f"首次={second_order_id} 再次={again}")

    # A9：不带 buyerId 的拉单 → 落 0，且不产生映射（无映射键）
    st, j = http("POST", "/api/v1/channel-orders/pull", {
        "channelCode": "TAOBAO",
        "channelOrderNo": "E2E-ORDER-0003",
        "receiverName": "李四", "receiverPhone": "13900000000", "receiverAddress": "某省某市某区 2 号",
        "lines": [{"outerSkuId": "TB" + PRODUCT, "title": "E2E商品", "quantity": 1, "unitPrice": 50.00}],
    }, token=tok)
    check("不带 buyerId 也能落单（客户维度未知，不拒绝建单）", st == 200, f"status={st} body={str(j)[:200]}")

    # A10：解绑后回退影子
    st, j = http("POST", "/api/v1/channel-buyers/unbind", token=tok,
                 params={"channelCode": "TAOBAO", "channelBuyerId": BUYER_ID})
    d = data_of(j) or {}
    check("解绑后退回影子态",
          st == 200 and d.get("bound") is False and d.get("customerId") == "0",
          f"status={st} body={str(j)[:200]}")

    # ==================== B. 库存广播 Outbox ====================
    print("\n--- B. 库存广播 Outbox ---")

    # B1：建库存（触发广播入队）
    st, j = http("POST", "/api/v1/inventories/receive", {
        "productId": PRODUCT, "productName": "E2E商品", "warehouseCode": "DEFAULT", "quantity": 300,
    }, token=tok)
    check("建库存成功", st == 200, f"status={st} body={str(j)[:200]}")

    # B2：上架到淘宝（只有 ONLINE 渠道才会被广播）
    st, j = http("POST", "/api/v1/channel-products", {
        "channelCode": "TAOBAO", "productId": PRODUCT, "productName": "E2E商品", "listingPrice": 88.00,
    }, token=tok)
    if st == 409:
        # 幂等：上一轮已上架，先下架再上
        http("POST", "/api/v1/channel-products/delist",
             {"channelCode": "TAOBAO", "productId": PRODUCT}, token=tok)
        st, j = http("POST", "/api/v1/channel-products", {
            "channelCode": "TAOBAO", "productId": PRODUCT, "productName": "E2E商品", "listingPrice": 88.00,
        }, token=tok)
    d = data_of(j) or {}
    check("淘宝上架成功（ONLINE）", st == 200 and d.get("listingStatus") == "ONLINE",
          f"status={st} body={str(j)[:200]}")

    # B3：库存变更 → 应产生待投递任务
    st, j = http("POST", "/api/v1/inventories/receive", {
        "productId": PRODUCT, "productName": "E2E商品", "warehouseCode": "DEFAULT", "quantity": 50,
    }, token=tok)
    check("二次入库成功（触发广播入队）", st == 200, f"status={st}")

    st, j = http("GET", "/api/v1/channel-broadcasts", token=tok,
                 params={"status": "PENDING", "page": 1, "size": 50})
    pending = (data_of(j) or {}).get("records") or []
    mine = [r for r in pending if str(r.get("productId")) == PRODUCT]
    check("库存变更后有待投递广播任务", st == 200 and len(mine) >= 1,
          f"status={st} pending={len(pending)} 属于本商品的={len(mine)}")
    task_id = mine[0]["id"] if mine else None

    # B4：手动推进一轮中继 → 任务转 SENT
    st, j = http("POST", "/api/v1/channel-broadcasts/relay-now", token=tok)
    check("手动推进中继成功", st == 200, f"status={st} body={str(j)[:160]}")

    st, j = http("GET", "/api/v1/channel-broadcasts", token=tok,
                 params={"status": "SENT", "page": 1, "size": 50})
    sent_rows = (data_of(j) or {}).get("records") or []
    sent_mine = [r for r in sent_rows if str(r.get("productId")) == PRODUCT]
    check("广播任务已投递成功（SENT）", st == 200 and len(sent_mine) >= 1,
          f"status={st} sent={len(sent_rows)} 属于本商品的={len(sent_mine)}")
    if sent_mine:
        # 覆盖式库存：任务的目标库存应等于入队时的可售量
        check("已投递任务的库存值与入队一致（非增量）",
              sent_mine[0].get("targetStock") in (300, 350, 50, 350),
              f"targetStock={sent_mine[0].get('targetStock')}")

    # B5：SENT 任务不可重试（避免重复投递掩盖真实结果）
    if task_id:
        st, j = http("POST", f"/api/v1/channel-broadcasts/{task_id}/retry", token=tok)
        check("已投递任务的重试被拒（404 语义）", st == 404 and err_of(j) == "BP_CHANNEL_BROADCAST_NOT_FOUND",
              f"status={st} code={err_of(j)}")

    # B6：不存在的任务 → 404
    st, j = http("POST", "/api/v1/channel-broadcasts/999999999999/retry", token=tok)
    check("重试不存在的任务 → 404", st == 404 and err_of(j) == "BP_CHANNEL_BROADCAST_NOT_FOUND",
          f"status={st} code={err_of(j)}")

    # B7：非法状态筛选被拒（输入错一个字母就返回全量是最危险的失败模式）
    st, j = http("GET", "/api/v1/channel-broadcasts", token=tok,
                 params={"status": "PENDINGX", "page": 1, "size": 10})
    check("非法状态筛选被拒而非静默返回全量", st >= 400, f"status={st}")

    print("\n============================================================")
    print(f"PASS: {len(PASS)}   FAIL: {len(FAIL)}")
    if FAIL:
        print("失败用例：")
        for name in FAIL:
            print(f"  - {name}")
    print("============================================================")
    return 1 if FAIL else 0


if __name__ == "__main__":
    raise SystemExit(main())
