#!/usr/bin/env python3
"""多渠道交易域前后端联调 E2E（逐功能点击测试）。

覆盖：渠道注册/启停 → 库存建储/预留 → 渠道商品四渠道上架 → 渠道订单拉单（含幂等）
→ 发货 → 物流轨迹 → 签收，外加负向用例。

结构（README §8 数据驱动化）：分节的「请求 + 判据」收敛为步骤字典列表，交给
e2e_lib.chain_runner.run_steps 通用执行器执行；断言出口注入 e2e_lib.report.check，
保持本脚本「✓/✗ + 通过 X 项，失败 Y 项 + 退出码 2」的原口径不变。

用法：
    python3 scripts/e2e/multichannel.py            # 走网关 8888
    python3 scripts/e2e/multichannel.py --direct   # 直连 8082（排查用）
"""
from __future__ import annotations

import argparse
import sys
import time
import urllib.parse

from e2e_lib import config, http, report
from e2e_lib.chain_runner import ChainCtx, judge, run_steps
from e2e_lib.report import check

PRODUCT = "900001"
WAREHOUSE = "DEFAULT"
CHANNELS = (("TAOBAO", "TB"), ("JD", "JD"), ("DOUYIN", "DY"), ("PDD", "PDD"))


def data_of(resp):
    return resp.get("data") if isinstance(resp, dict) else None


def records_of(resp):
    """取分页响应 records（等价原各处手写的 ``(data_of(j) or {}).get("records") or []``）。"""
    return (data_of(resp) or {}).get("records") or []


def err_code_of(j):
    """取业务码：ApiResponse 信封走 data.errorCode，否则回落顶层 code（原内联表达式收口）。"""
    return ((data_of(j) or {}) if isinstance(j, dict) else {}).get("errorCode") or (j or {}).get("code")


def order_status_of(order_id):
    """取订单状态；返回 (http_status, status 或 None)。

    用于断言发货联动。发货是 AFTER_COMMIT 异步推进订单，理论上存在毫秒级延迟，
    故这里短暂重试几次而不是只查一次——否则会把"最终一致"误判成"联动失效"。
    """
    for _ in range(5):
        st, j = http.call("GET", f"/orders/{order_id}")
        body = data_of(j) or {}
        cur = body.get("status") if isinstance(body, dict) else None
        if cur:
            return st, cur
        time.sleep(0.4)
    return st, cur


# ---------------- 发货链路的分支取数（等价原分节5 的 if/else） ----------------

def _taobao_order(c):
    """发货链路主单：拉单登记表里 TAOBAO 那单（拉单失败时为 None）。"""
    return c.order_ids.get("TAOBAO", (None, None))[1]


def _has_taobao(c):
    """发货链路是否可进入（等价原 if taobao_order 分支）。"""
    return bool(_taobao_order(c))


def _can_cancel(c):
    """「构造已取消订单」负向用例是否可执行（主单在且取消单拉取成功）。"""
    return _has_taobao(c) and bool(c.cancelled_order_id)


def _emit(name, expect, actual, detail=""):
    """断言出口：判定复用 chain_runner.judge，落点走 report.check ——
    保持 ✓/✗ 打印与 report.PASS/FAIL 计数口径（汇总与退出码 2 均不变）。"""
    return report.check(name, judge(expect, actual), detail)


def _rpc(method, path, body, token):
    """执行器请求入口：本脚本全程走默认会话（登录时已 set_default），不用 token 参数。"""
    return http.call(method, path, body)


# ---------------- 分节步骤工厂（循环变量进工厂作用域，避免闭包都取到末次值） ----------------

def _list_steps(ch, pfx):
    """一个渠道的上架步骤：商品上架 → ONLINE。"""
    return [
        {"method": "POST", "path": "/channel-products",
         "body": {"channelCode": ch, "productId": PRODUCT, "productName": "联调测试商品",
                  "listingPrice": 99.00},
         "checks": [{"name": f"{ch} 商品上架 → ONLINE", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("listingStatus") == "ONLINE"
                     and (data_of(j) or {}).get("channelProductId") == pfx + PRODUCT,
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:200]}"}]},
    ]


def _pull_steps(ch, pfx):
    """一个渠道的拉单三步：落单 → 同号幂等 → 状态回传。"""
    no = f"{pfx}E2E{abs(hash(ch)) % 100000}"
    payload = {
        "channelCode": ch, "channelOrderNo": no, "buyerNick": f"buyer_{ch.lower()}",
        "payAmount": 198.00, "receiverName": "张三", "receiverPhone": "13800000000",
        "receiverAddress": "测试地址",
        "lines": [{"outerSkuId": pfx + PRODUCT, "title": "联调测试商品", "quantity": 1, "unitPrice": 99.00}],
    }

    def save(c, st, j):
        # 落单后登记 (渠道单号, 订单 id)，供分节 5 按渠道取数
        c.order_ids[ch] = (no, c.oid)

    return [
        {"method": "POST", "path": "/channel-orders/pull", "body": payload,
         "extract": {"oid": lambda c, st, j: data_of(j)},
         "checks": [{"name": f"{ch} 渠道订单落单", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and bool(data_of(j)),
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:200]}"}],
         "post": save},
        # 幂等：同号重推应返回同一订单
        {"method": "POST", "path": "/channel-orders/pull", "body": payload,
         "checks": [{"name": f"{ch} 重复推送幂等", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and data_of(j) == c.oid,
                     "detail": lambda c, st, j: f"status={st} 原={c.oid} 新={data_of(j)}"}]},
        # 状态回传（发货信息回传渠道）
        # ★ 必须带 logisticsCompany + trackingNo：ChannelOrderController.AckShipmentReq 两个字段都是
        #   @NotBlank。早期本端点只收渠道订单号、运单号由服务端写死，回传给渠道的永远是同一串假单号
        #   （渠道展示物流与实际不符却返回成功），故改为由调用方传承运商与真实运单号。
        #   此前脚本不带 body 调用 ⇒ 恒 400 COMMON_VALIDATION_FAILED，四个渠道一起假红。
        {"method": "POST", "path": f"/channel-orders/{ch}/{no}/ack",
         "body": {"logisticsCompany": "SF", "trackingNo": f"SF{no}"},
         "checks": [{"name": f"{ch} 订单状态回传", "kind": "cond",
                     "actual": lambda c, st, j: st == 200,
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:160]}"}]},
    ]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--direct", action="store_true")
    args = ap.parse_args()
    if args.direct:
        # 直连后端：API 与登录地址都切到 8082（LOGIN_BASE 在导入时已固化，需一并覆盖）
        config.BASE = "http://127.0.0.1:8082/api/v1"
        config.LOGIN_BASE = config.BASE

    print(f"=== 多渠道交易域 E2E · base={config.BASE} ===")

    # ---------- 0. 登录 ----------
    # 常见部署口令不同，逐个尝试（公共层凭据 BONE_E2E_PWD 作首选，其余为历史兜底口令）
    tok = None
    for pwd in dict.fromkeys((config.PWD, "admin123", "Admin@123", "123456", "bone123")):
        sess = http.Session(config.USER, pwd)
        tok = sess.login(fatal=False)
        if tok:
            # 后续 http.call 全走这份会话，调用点不再逐个传 token
            http.set_default(sess)
            break
    if not check("登录获取 JWT", bool(tok), f"各口令登录均失败 base={config.BASE}"):
        print("\n登录失败，后续用例无法执行"); return 1
    if not check("JWT 令牌非空", bool(tok), str(tok)[:160]):
        return 1

    # ---------- 0.5 幂等重置：把测试商品残留的渠道商品行归位 ----------
    # 为什么需要：上架用例要求商品处于 UNLISTED 才能上架（否则 409 状态冲突），
    # 而上一轮跑完后商品停在 ONLINE/OFFLINE。不重置时脚本**第二次运行必然红**，
    # 让人误以为功能坏了 —— 实为测试数据残留。这里用 API 自身的能力归位（不直接改库），
    # 顺带把「下架」链路也走了一遍。
    st, j = http.call("GET", "/channel-products?" + urllib.parse.urlencode(
        {"page": 1, "size": 50, "productId": PRODUCT}))
    for r in records_of(j):
        if r.get("listingStatus") == "ONLINE":
            http.call("POST", "/channel-products/delist",
                      {"channelCode": r["channelCode"], "productId": PRODUCT})

    # ---------- 链路定义：步骤 + 判据（数据），执行交给通用执行器 ----------
    steps = [
        # ---------- 1. 渠道 ----------
        "\n--- 1. 渠道管理 ---",
        {"method": "GET", "path": "/channels?" + urllib.parse.urlencode({"page": 1, "size": 10}),
         "checks": [
             {"name": "渠道分页列表", "kind": "cond",
              "actual": lambda c, st, j: st == 200 and len(records_of(j)) >= 4,
              "detail": lambda c, st, j: f"status={st} n={len(records_of(j))}"},
             {"name": "四大渠道已预置", "kind": "cond",
              "actual": lambda c, st, j: {"TAOBAO", "JD", "DOUYIN", "PDD"}
              <= {r["channelCode"] for r in records_of(j)},
              "detail": lambda c, st, j: f"实际={sorted({r['channelCode'] for r in records_of(j)})}"},
         ]},
        {"method": "GET", "path": "/channels/TAOBAO",
         "checks": [{"name": "渠道详情", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("channelCode") == "TAOBAO",
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "POST", "path": "/channels/TAOBAO/disable",
         "checks": [{"name": "渠道停用", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("enabled") is False,
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "POST", "path": "/channels/TAOBAO/enable",
         "checks": [{"name": "渠道启用", "kind": "cond", "actual": lambda c, st, j: st == 200}]},
        {"method": "POST",
         "path": "/channels/TAOBAO/order-sync?" + urllib.parse.urlencode({"enabled": "true"}),
         "checks": [{"name": "设置订单自动同步", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("orderSyncEnabled") is True,
                     "detail": lambda c, st, j: f"status={st}"}]},

        # ---------- 2. 库存 ----------
        "\n--- 2. 库存管理 ---",
        {"method": "POST", "path": "/inventories/receive",
         "body": {"productId": PRODUCT, "productName": "联调测试商品",
                  "warehouseCode": WAREHOUSE, "quantity": 500},
         "checks": [{"name": "建库存/入库", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("availableQty", 0) >= 500,
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:160]}"}]},
        {"method": "GET", "path": "/inventories?" + urllib.parse.urlencode({"page": 1, "size": 10}),
         "checks": [{"name": "库存分页列表", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and len(records_of(j)) >= 1,
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "GET",
         "path": f"/inventories/{PRODUCT}?" + urllib.parse.urlencode({"warehouseCode": WAREHOUSE}),
         "checks": [{"name": "库存详情", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and data_of(j) is not None,
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "POST", "path": "/inventories/reserve",
         "body": {"productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 10},
         "checks": [{"name": "库存预留", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("reservedQty", 0) >= 10,
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "POST", "path": "/inventories/release",
         "body": {"productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 10},
         "checks": [{"name": "释放预留", "kind": "cond", "actual": lambda c, st, j: st == 200}]},
        {"method": "POST", "path": "/inventories/safety-stock",
         "body": {"productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 20},
         "checks": [{"name": "设置安全库存", "kind": "cond", "actual": lambda c, st, j: st == 200}]},
        # 负向：预留超量 → 409 BP_INVENTORY_INSUFFICIENT
        {"method": "POST", "path": "/inventories/reserve",
         "body": {"productId": PRODUCT, "warehouseCode": WAREHOUSE, "quantity": 999999},
         "checks": [{"name": "超量预留被拒（409 库存不足）", "kind": "cond",
                     "actual": lambda c, st, j: st == 409,
                     "detail": lambda c, st, j: f"status={st} code={err_code_of(j)}"}]},

        # ---------- 3. 渠道商品上架（四渠道） ----------
        "\n--- 3. 渠道商品上架 ---",
        *[s for ch, pfx in CHANNELS for s in _list_steps(ch, pfx)],
        {"method": "GET",
         "path": "/channel-products?" + urllib.parse.urlencode(
             {"page": 1, "size": 20, "productId": PRODUCT}),
         "checks": [{"name": "渠道商品列表（4 条）", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and len(records_of(j)) == 4,
                     "detail": lambda c, st, j: f"status={st} n={len(records_of(j))}"}]},
        # 幂等/状态机：重复上架 → 409 状态冲突
        {"method": "POST", "path": "/channel-products",
         "body": {"channelCode": "TAOBAO", "productId": PRODUCT,
                  "productName": "联调测试商品", "listingPrice": 99.00},
         "checks": [{"name": "重复上架被拒（409 状态冲突）", "kind": "cond",
                     "actual": lambda c, st, j: st == 409,
                     "detail": lambda c, st, j: f"status={st}"}]},
        # 库存广播
        {"method": "POST",
         "path": "/channel-products/sync-inventory?" + urllib.parse.urlencode(
             {"productId": PRODUCT, "stock": 480}),
         "checks": [{"name": "库存广播到全部已上架渠道", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and len(data_of(j) or []) == 4,
                     "detail": lambda c, st, j: f"status={st} n={len(data_of(j) or [])}"}]},

        # ---------- 4. 渠道订单拉单（四渠道 + 幂等） ----------
        "\n--- 4. 渠道订单接入 ---",
        *[s for ch, pfx in CHANNELS for s in _pull_steps(ch, pfx)],
        # 负向：非法渠道码
        {"method": "POST", "path": "/channel-orders/pull",
         "body": {"channelCode": "NOT_A_CHANNEL", "channelOrderNo": "X1",
                  "lines": [{"outerSkuId": "TB" + PRODUCT, "quantity": 1, "unitPrice": 1}]},
         "checks": [{"name": "非法渠道码被拒（400）", "kind": "cond",
                     "actual": lambda c, st, j: st == 400,
                     "detail": lambda c, st, j: f"status={st}"}]},

        # ---------- 5. 发货物流 ----------
        "\n--- 5. 发货物流 ---",
        # 取不到淘宝订单：只落一条 FAIL，不进入发货链路（等价原 if/else 的空分支）
        {"checks": [{"name": "取淘宝订单用于发货", "kind": "cond", "actual": False,
                     "detail": "订单为空"}],
         "when": lambda c: not _has_taobao(c)},
        # 先造一条「已取消」订单供负向用例使用（不能复用上面那单——它要正常走完发货链路）。
        {"method": "POST", "path": "/channel-orders/pull", "when": _has_taobao,
         "body": lambda c: {"channelCode": "TAOBAO",
                            "channelOrderNo": f"MC-CANCEL-{int(time.time())}",
                            "buyerNick": "取消测试", "payAmount": 88.00,
                            "receiverName": "李四", "receiverPhone": "13900000000",
                            "receiverAddress": "取消单地址",
                            "lines": [{"outerSkuId": PRODUCT, "title": "取消测试商品",
                                       "quantity": 1, "unitPrice": 88.00}]},
         "extract": {"cancelled_order_id": lambda c, st, j: data_of(j),
                     "taobao_order": lambda c, st, j: _taobao_order(c)},
         "checks": [{"name": "构造已取消订单用于负向用例", "kind": "cond",
                     "when": lambda c: not c.cancelled_order_id,
                     "actual": False,
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:160]}"}]},
        {"method": "POST", "path": "/orders/{cancelled_order_id}/cancel", "when": _can_cancel},
        {"call": lambda c: order_status_of(c.cancelled_order_id), "when": _can_cancel,
         "checks": [{"name": "构造已取消订单用于负向用例", "kind": "cond",
                     "actual": lambda c, st, j: j == "CANCELLED",
                     "detail": lambda c, st, j: f"orderStatus={j}"}]},
        {"method": "POST", "path": "/shipments", "when": _has_taobao,
         "body": lambda c: {"orderId": _taobao_order(c), "channelCode": "TAOBAO",
                            "receiverName": "张三", "receiverPhone": "13800000000",
                            "receiverAddress": "测试地址"},
         "extract": {"sid": lambda c, st, j: (data_of(j) or {}).get("id")},
         "checks": [{"name": "创建发货单", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("status") == "CREATED",
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:200]}"}]},
        # 负向：无运单号发货
        {"method": "POST", "path": "/shipments/{sid}/ship", "when": _has_taobao,
         "body": {"logisticsCompany": "顺丰速运", "trackingNo": ""},
         "checks": [{"name": "空运单号发货被拒（400）", "kind": "cond",
                     "actual": lambda c, st, j: st == 400,
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "POST", "path": "/shipments/{sid}/ship", "when": _has_taobao,
         "body": {"logisticsCompany": "顺丰速运", "trackingNo": "SF1234567890"},
         "checks": [{"name": "发货并回传渠道", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("status") == "SHIPPED"
                     and (data_of(j) or {}).get("channelAck") is True,
                     "detail": lambda c, st, j: f"status={st} body={str(j)[:220]}"}]},

        # ↓ 订单状态联动断言（2026-10-06 新增）。
        # 这两条曾长期缺失，导致「发货单 SIGNED / 订单仍 CREATED」的割裂缺陷反复隐身：
        # E2E 全绿也证明不了订单侧正常，因为根本没有一项断言去看订单状态。
        {"call": lambda c: order_status_of(_taobao_order(c)), "when": _has_taobao,
         "checks": [{"name": "发货后订单联动为 SHIPPED", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and j == "SHIPPED",
                     "detail": lambda c, st, j: f"status={st} orderStatus={j}"}]},

        {"method": "POST", "path": "/shipments/{sid}/traces/sync", "when": _has_taobao,
         "checks": [{"name": "同步物流轨迹", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and len(data_of(j) or []) >= 1,
                     "detail": lambda c, st, j: f"status={st} n={len(data_of(j) or [])}"}]},
        {"method": "GET", "path": "/shipments/{sid}/traces", "when": _has_taobao,
         "checks": [{"name": "查询本地轨迹", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and len(data_of(j) or []) >= 1,
                     "detail": lambda c, st, j: f"status={st}"}]},

        {"method": "POST", "path": "/shipments/{sid}/sign", "when": _has_taobao,
         "checks": [{"name": "签收", "kind": "cond",
                     "actual": lambda c, st, j: st == 200
                     and (data_of(j) or {}).get("status") == "SIGNED",
                     "detail": lambda c, st, j: f"status={st}"}]},

        {"call": lambda c: order_status_of(_taobao_order(c)), "when": _has_taobao,
         "checks": [{"name": "签收后订单联动为 DELIVERED", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and j == "DELIVERED",
                     "detail": lambda c, st, j: f"status={st} orderStatus={j}"}]},

        # 负向：已取消/未支付订单不得建发货单（守卫回归）。
        # 修复前ShipmentApplicationService.create 完全不校验订单状态，
        # 于是已取消订单也能发货并签收——这是真实业务漏洞，不只是状态显示问题。
        {"method": "POST", "path": "/shipments", "when": _has_taobao,
         "body": lambda c: {"orderId": c.cancelled_order_id, "channelCode": "TAOBAO",
                            "receiverName": "李四", "receiverPhone": "13900000000",
                            "receiverAddress": "取消单地址"},
         "checks": [{"name": "已取消订单建发货单被拒（409 状态冲突）", "kind": "cond",
                     "actual": lambda c, st, j: st == 409,
                     "detail": lambda c, st, j: f"status={st}"}]},

        {"method": "GET", "when": _has_taobao,
         "path": "/shipments?" + urllib.parse.urlencode(
             {"page": 1, "size": 10, "channelCode": "TAOBAO"}),
         "checks": [{"name": "发货单列表", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and len(records_of(j)) >= 1,
                     "detail": lambda c, st, j: f"status={st}"}]},
        {"method": "GET", "path": "/shipments/by-order/{taobao_order}", "when": _has_taobao,
         "checks": [{"name": "按订单查发货单", "kind": "cond",
                     "actual": lambda c, st, j: st == 200 and data_of(j) is not None,
                     "detail": lambda c, st, j: f"status={st}"}]},

        # ---------- 6. 下架 ----------
        "\n--- 6. 商品下架 ---",
        *[{"method": "POST", "path": "/channel-products/delist",
           "body": {"channelCode": ch, "productId": PRODUCT},
           "checks": [{"name": f"{ch} 商品下架 → OFFLINE", "kind": "cond",
                       "actual": lambda c, st, j: st == 200
                       and (data_of(j) or {}).get("listingStatus") == "OFFLINE",
                       "detail": lambda c, st, j: f"status={st} body={str(j)[:200]}"}]}
          for ch, _pfx in CHANNELS],
    ]
    ctx = ChainCtx(_rpc, cid="MC")
    ctx.order_ids = {}   # 渠道 → (渠道单号, 订单 id)：拉单步骤的 post 钩子写入，分节 5 读取
    run_steps(steps, ctx, emit=_emit)

    # ---------- 汇总 ----------
    # 退出码保留原口径：有失败返回 2（消费方 verify-multichannel 脚本按非零判红）
    report.summary("多渠道交易域 E2E 汇总")
    return 2 if report.FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
