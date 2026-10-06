#!/usr/bin/env python3
"""多渠道交易域前后端联调 E2E（逐功能点击测试）。

覆盖：渠道注册/启停 → 库存建储/预留 → 渠道商品四渠道上架 → 渠道订单拉单（含幂等）
→ 发货 → 物流轨迹 → 签收，外加负向用例。

用法：
    python3 scripts/e2e/multichannel.py            # 走网关 8888
    python3 scripts/e2e/multichannel.py --direct   # 直连 8082（排查用）
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BONE_E2E_BASE", "http://127.0.0.1:8888")
PRODUCT = "900001"
WAREHOUSE = "DEFAULT"

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
    # 本机有 HTTP_PROXY，探测 127.0.0.1 必须绕过，否则未启动会返回 502 而非 000
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


def order_status_of(order_id, token):
    """取订单状态；返回 (http_status, status 或 None)。

    用于断言发货联动。发货是 AFTER_COMMIT 异步推进订单，理论上存在毫秒级延迟，
    故这里短暂重试几次而不是只查一次——否则会把"最终一致"误判成"联动失效"。
    """
    for _ in range(5):
        st, j = http("GET", f"/api/v1/orders/{order_id}", token=token)
        body = data_of(j) or {}
        cur = body.get("status") if isinstance(body, dict) else None
        if cur:
            return st, cur
        time.sleep(0.4)
    return st, cur


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--direct", action="store_true")
    args = ap.parse_args()
    global BASE
    if args.direct:
        BASE = "http://127.0.0.1:8082"

    print(f"=== 多渠道交易域 E2E · base={BASE} ===")

    # ---------- 0. 登录 ----------
    st, j = http("POST", "/api/v1/iam/login", {"username": "admin", "password": "admin123"})
    if st != 200:
        # 常见部署口令不同，逐個尝试
        for pwd in ("Admin@123", "123456", "bone123"):
            st, j = http("POST", "/api/v1/iam/login", {"username": "admin", "password": pwd})
            if st == 200:
                break
    if not check("登录获取 JWT", st == 200, f"status={st} body={str(j)[:160]}"):
        print("\n登录失败，后续用例无法执行"); return 1
    tok = (data_of(j) or {}).get("accessToken") or (data_of(j) or {}).get("token")
    if not check("JWT 令牌非空", bool(tok), str(data_of(j))[:160]):
        return 1

    # ---------- 0.5 幂等重置：把测试商品残留的渠道商品行归位 ----------
    # 为什么需要：上架用例要求商品处于 UNLISTED 才能上架（否则 409 状态冲突），
    # 而上一轮跑完后商品停在 ONLINE/OFFLINE。不重置时脚本**第二次运行必然红**，
    # 让人误以为功能坏了 —— 实为测试数据残留。这里用 API 自身的能力归位（不直接改库），
    # 顺带把「下架」链路也走了一遍。
    st, j = http("GET", "/api/v1/channel-products", token=tok,
                 params={"page": 1, "size": 50, "productId": PRODUCT})
    for r in (data_of(j) or {}).get("records") or []:
        if r.get("listingStatus") == "ONLINE":
            http("POST", "/api/v1/channel-products/delist",
                 {"channelCode": r["channelCode"], "productId": PRODUCT}, token=tok)

    # ---------- 1. 渠道 ----------
    print("\n--- 1. 渠道管理 ---")
    st, j = http("GET", "/api/v1/channels", token=tok, params={"page": 1, "size": 10})
    rows = (data_of(j) or {}).get("records") or []
    check("渠道分页列表", st == 200 and len(rows) >= 4, f"status={st} n={len(rows)}")
    codes = {r["channelCode"] for r in rows}
    check("四大渠道已预置", {"TAOBAO", "JD", "DOUYIN", "PDD"} <= codes, f"实际={sorted(codes)}")

    st, j = http("GET", "/api/v1/channels/TAOBAO", token=tok)
    check("渠道详情", st == 200 and (data_of(j) or {}).get("channelCode") == "TAOBAO", f"status={st}")

    st, j = http("POST", "/api/v1/channels/TAOBAO/disable", token=tok)
    check("渠道停用", st == 200 and (data_of(j) or {}).get("enabled") is False, f"status={st}")
    st, _ = http("POST", "/api/v1/channels/TAOBAO/enable", token=tok)
    check("渠道启用", st == 200)
    st, j = http("POST", "/api/v1/channels/TAOBAO/order-sync", token=tok, params={"enabled": "true"})
    check("设置订单自动同步", st == 200 and (data_of(j) or {}).get("orderSyncEnabled") is True, f"status={st}")

    # ---------- 2. 库存 ----------
    print("\n--- 2. 库存管理 ---")
    st, j = http("POST", "/api/v1/inventories/receive", {
        "productId": PRODUCT, "productName": "联调测试商品", "warehouseCode": WAREHOUSE, "quantity": 500,
    }, token=tok)
    inv = data_of(j) or {}
    check("建库存/入库", st == 200 and inv.get("availableQty", 0) >= 500, f"status={st} body={str(j)[:160]}")

    st, j = http("GET", "/api/v1/inventories", token=tok, params={"page": 1, "size": 10})
    check("库存分页列表", st == 200 and len((data_of(j) or {}).get("records") or []) >= 1, f"status={st}")

    st, j = http("GET", f"/api/v1/inventories/{PRODUCT}", token=tok, params={"warehouseCode": WAREHOUSE})
    check("库存详情", st == 200 and data_of(j) is not None, f"status={st}")

    st, j = http("POST", "/api/v1/inventories/reserve", {
        "productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 10}, token=tok)
    check("库存预留", st == 200 and (data_of(j) or {}).get("reservedQty", 0) >= 10, f"status={st}")
    st, _ = http("POST", "/api/v1/inventories/release", {
        "productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 10}, token=tok)
    check("释放预留", st == 200)
    st, _ = http("POST", "/api/v1/inventories/safety-stock", {
        "productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 20}, token=tok)
    check("设置安全库存", st == 200)

    # 负向：预留超量 → 409 BP_INVENTORY_INSUFFICIENT
    st, j = http("POST", "/api/v1/inventories/reserve", {
        "productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 999999}, token=tok)
    code = ((data_of(j) or {}) if isinstance(j, dict) else {}).get("errorCode") or (j or {}).get("code")
    check("超量预留被拒（409 库存不足）", st == 409, f"status={st} code={code}")

    # ---------- 3. 渠道商品上架（四渠道） ----------
    print("\n--- 3. 渠道商品上架 ---")
    for ch, pfx in (("TAOBAO", "TB"), ("JD", "JD"), ("DOUYIN", "DY"), ("PDD", "PDD")):
        st, j = http("POST", "/api/v1/channel-products", {
            "channelCode": ch, "productId": PRODUCT, "productName": "联调测试商品", "listingPrice": 99.00,
        }, token=tok)
        d = data_of(j) or {}
        check(f"{ch} 商品上架 → ONLINE",
              st == 200 and d.get("listingStatus") == "ONLINE" and d.get("channelProductId") == pfx + PRODUCT,
              f"status={st} body={str(j)[:200]}")

    st, j = http("GET", "/api/v1/channel-products", token=tok, params={"page": 1, "size": 20, "productId": PRODUCT})
    rows = (data_of(j) or {}).get("records") or []
    check("渠道商品列表（4 条）", st == 200 and len(rows) == 4, f"status={st} n={len(rows)}")

    # 幂等/状态机：重复上架 → 409 状态冲突
    st, j = http("POST", "/api/v1/channel-products", {
        "channelCode": "TAOBAO", "productId": PRODUCT, "productName": "联调测试商品", "listingPrice": 99.00,
    }, token=tok)
    check("重复上架被拒（409 状态冲突）", st == 409, f"status={st}")

    # 库存广播
    st, j = http("POST", "/api/v1/channel-products/sync-inventory", token=tok,
                 params={"productId": PRODUCT, "stock": 480})
    check("库存广播到全部已上架渠道", st == 200 and len(data_of(j) or []) == 4, f"status={st} n={len(data_of(j) or [])}")

    # ---------- 4. 渠道订单拉单（四渠道 + 幂等） ----------
    print("\n--- 4. 渠道订单接入 ---")
    order_ids = {}
    for ch, pfx in (("TAOBAO", "TB"), ("JD", "JD"), ("DOUYIN", "DY"), ("PDD", "PDD")):
        no = f"{pfx}E2E{abs(hash(ch)) % 100000}"
        payload = {
            "channelCode": ch, "channelOrderNo": no, "buyerNick": f"buyer_{ch.lower()}",
            "payAmount": 198.00, "receiverName": "张三", "receiverPhone": "13800000000",
            "receiverAddress": "测试地址",
            "lines": [{"outerSkuId": pfx + PRODUCT, "title": "联调测试商品", "quantity": 1, "unitPrice": 99.00}],
        }
        st, j = http("POST", "/api/v1/channel-orders/pull", payload, token=tok)
        oid = data_of(j)
        check(f"{ch} 渠道订单落单", st == 200 and bool(oid), f"status={st} body={str(j)[:200]}")
        order_ids[ch] = (no, oid)
        # 幂等：同号重推应返回同一订单
        st2, j2 = http("POST", "/api/v1/channel-orders/pull", payload, token=tok)
        check(f"{ch} 重复推送幂等", st2 == 200 and data_of(j2) == oid, f"status={st2} 原={oid} 新={data_of(j2)}")
        # 状态回传
        st3, _ = http("POST", f"/api/v1/channel-orders/{ch}/{no}/ack", token=tok)
        check(f"{ch} 订单状态回传", st3 == 200 and (data_of(j2) is not None or True) and st3 == 200, f"status={st3}")

    # 负向：非法渠道码
    st, _ = http("POST", "/api/v1/channel-orders/pull", {
        "channelCode": "NOT_A_CHANNEL", "channelOrderNo": "X1",
        "lines": [{"outerSkuId": "TB" + PRODUCT, "quantity": 1, "unitPrice": 1}],
    }, token=tok)
    check("非法渠道码被拒（400）", st == 400, f"status={st}")

    # ---------- 5. 发货物流 ----------
    print("\n--- 5. 发货物流 ---")
    taobao_order = order_ids.get("TAOBAO", (None, None))[1]
    if not taobao_order:
        check("取淘宝订单用于发货", False, "订单为空")
    else:
        # 先造一条「已取消」订单供负向用例使用（不能复用上面那单——它要正常走完发货链路）。
        st, j = http("POST", "/api/v1/channel-orders/pull", {
            "channelCode": "TAOBAO", "channelOrderNo": f"MC-CANCEL-{int(time.time())}",
            "buyerNick": "取消测试", "payAmount": 88.00,
            "receiverName": "李四", "receiverPhone": "13900000000",
            "receiverAddress": "取消单地址",
            "lines": [{"outerSkuId": PRODUCT, "title": "取消测试商品", "quantity": 1, "unitPrice": 88.00}],
        }, token=tok)
        cancelled_order_id = data_of(j)
        if cancelled_order_id:
            http("POST", f"/api/v1/orders/{cancelled_order_id}/cancel", token=tok)
            st2, cs = order_status_of(cancelled_order_id, tok)
            check("构造已取消订单用于负向用例", cs == "CANCELLED", f"orderStatus={cs}")
        else:
            check("构造已取消订单用于负向用例", False, f"status={st} body={str(j)[:160]}")

        st, j = http("POST", "/api/v1/shipments", {
            "orderId": taobao_order, "channelCode": "TAOBAO", "receiverName": "张三",
            "receiverPhone": "13800000000", "receiverAddress": "测试地址",
        }, token=tok)
        ship = data_of(j) or {}
        check("创建发货单", st == 200 and ship.get("status") == "CREATED", f"status={st} body={str(j)[:200]}")
        sid = ship.get("id")

        # 负向：无运单号发货
        st, _ = http("POST", f"/api/v1/shipments/{sid}/ship", {"logisticsCompany": "顺丰速运", "trackingNo": ""}, token=tok)
        check("空运单号发货被拒（400）", st == 400, f"status={st}")

        st, j = http("POST", f"/api/v1/shipments/{sid}/ship",
                     {"logisticsCompany": "顺丰速运", "trackingNo": "SF1234567890"}, token=tok)
        d = data_of(j) or {}
        check("发货并回传渠道", st == 200 and d.get("status") == "SHIPPED" and d.get("channelAck") is True,
              f"status={st} body={str(j)[:220]}")

        # ↓ 订单状态联动断言（2026-10-06 新增）。
        # 这两条曾长期缺失，导致「发货单 SIGNED / 订单仍 CREATED」的割裂缺陷反复隐身：
        # E2E 全绿也证明不了订单侧正常，因为根本没有一项断言去看订单状态。
        st, j = order_status_of(taobao_order, tok)
        check("发货后订单联动为 SHIPPED", st == 200 and j == "SHIPPED", f"status={st} orderStatus={j}")

        st, j = http("POST", f"/api/v1/shipments/{sid}/traces/sync", token=tok)
        check("同步物流轨迹", st == 200 and len(data_of(j) or []) >= 1, f"status={st} n={len(data_of(j) or [])}")
        st, j = http("GET", f"/api/v1/shipments/{sid}/traces", token=tok)
        check("查询本地轨迹", st == 200 and len(data_of(j) or []) >= 1, f"status={st}")

        st, j = http("POST", f"/api/v1/shipments/{sid}/sign", token=tok)
        check("签收", st == 200 and (data_of(j) or {}).get("status") == "SIGNED", f"status={st}")

        st, j = order_status_of(taobao_order, tok)
        check("签收后订单联动为 DELIVERED", st == 200 and j == "DELIVERED", f"status={st} orderStatus={j}")

        # 负向：已取消/未支付订单不得建发货单（守卫回归）。
        # 修复前ShipmentApplicationService.create 完全不校验订单状态，
        # 于是已取消订单也能发货并签收——这是真实业务漏洞，不只是状态显示问题。
        st, _ = http("POST", "/api/v1/shipments", {
            "orderId": cancelled_order_id, "channelCode": "TAOBAO", "receiverName": "李四",
            "receiverPhone": "13900000000", "receiverAddress": "取消单地址",
        }, token=tok)
        check("已取消订单建发货单被拒（409 状态冲突）", st == 409, f"status={st}")

        st, j = http("GET", "/api/v1/shipments", token=tok, params={"page": 1, "size": 10, "channelCode": "TAOBAO"})
        check("发货单列表", st == 200 and len((data_of(j) or {}).get("records") or []) >= 1, f"status={st}")
        st, j = http("GET", f"/api/v1/shipments/by-order/{taobao_order}", token=tok)
        check("按订单查发货单", st == 200 and data_of(j) is not None, f"status={st}")

    # ---------- 6. 下架 ----------
    print("\n--- 6. 商品下架 ---")
    for ch in ("TAOBAO", "JD", "DOUYIN", "PDD"):
        st, j = http("POST", "/api/v1/channel-products/delist", {
            "channelCode": ch, "productId": PRODUCT}, token=tok)
        d = data_of(j) or {}
        check(f"{ch} 商品下架 → OFFLINE", st == 200 and d.get("listingStatus") == "OFFLINE",
              f"status={st} body={str(j)[:200]}")

    # ---------- 汇总 ----------
    print("\n" + "=" * 60)
    print(f"PASS: {len(PASS)}   FAIL: {len(FAIL)}")
    if FAIL:
        print("失败用例：")
        for f in FAIL:
            print("  - " + f)
    print("=" * 60)
    return 0 if not FAIL else 2


if __name__ == "__main__":
    sys.exit(main())
