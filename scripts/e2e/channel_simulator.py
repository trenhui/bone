#!/usr/bin/env python3
"""支付渠道模拟器（本地联调 / 回归用）。

**它模拟的是「渠道那一方」，不是「我方后端」** —— 真实生产里 `PAYING → SUCCESS` 由渠道
server-to-server 回调 `/api/v1/payments/callback` 推进，而渠道不在本地，所以这条最后一跳
在本机永远没人来走。本脚本就是替渠道走这一跳。

设计上刻意遵守三条边界，否则它就会变成"绕过验签的后门"：

1. **不绕过验签**：签名用与 `MockPaymentSignaturePortAdapter` 完全相同的 payload 口径与
   HMAC 算法现算（`paymentId|channelTradeNo|amount.stripTrailingZeros().toPlainString()`），
   服务端照常执行 `verify()`。本脚本只是"扮演渠道生成签名的人"，不改变任何校验路径。
2. **不带用户凭证**：真实渠道回调不可能持有我方 JWT。故本脚本**不登录、不传
   Authorization**，走的是网关 `permitAll` 白名单那条真实链路——这同时也在回归
   "网关确实放通了 callback" 这一条（若哪天白名单被误删，这里会 401 而不是 200）。
3. **金额取自服务端**：默认读支付单的 `amount` 作为 `paidAmount`，不接受手输金额。
   手输会让"金额被篡改"这类真实风险在联调中永远测不出来。

用法::

    # 推进某笔支付单为成功（最常用）
    python3 scripts/e2e/channel_simulator.py --payment-id 761763556105388032

    # 模拟渠道通知支付失败
    python3 scripts/e2e/channel_simulator.py --payment-id <pid> --action fail

    # 指定租户（默认 0；UI 上 tenant_admin 在 1001）
    python3 scripts/e2e/channel_simulator.py --payment-id <pid> --tenant 1001

    # 故意伪造签名，验证验签确实在工作（期望 401）
    python3 scripts/e2e/channel_simulator.py --payment-id <pid> --tamper-signature

退出码：0=成功推进，1=被服务端拒绝或状态不符合预期（可直接接 CI）。
"""

import argparse
import json
import sys
import time

import lib

# 与 MockPaymentSignaturePortAdapter#MOCK_SECRET 一致。
# 注意：这是"渠道侧密钥"，模拟器持有它是合理的（真实渠道也持有自己的签名密钥）。
# 它硬编码在源码里是 Mock 验签适配器的既有事实，本脚本只是复刻，不新增风险。
ACTION_SUCCESS = "success"
ACTION_FAIL = "fail"


def _data(resp):
    """取 ApiResponse 的 data 本体（适配 {code,data} 信封）。"""
    if not isinstance(resp, dict):
        return None
    d = resp.get("data")
    return d if isinstance(d, dict) else None


def fetch_payment(payment_id, token, tenant):
    """读支付单详情（读端点需凭证，故这里用管理员 token）。"""
    st, j = lib.http("GET", f"/api/v1/payments/{payment_id}", token=token, tenant=tenant)
    return st, _data(j), j


def main():
    ap = argparse.ArgumentParser(
        description="支付渠道模拟器：替渠道走 PAYING → SUCCESS/FAIL 的回调那一跳"
    )
    ap.add_argument("--payment-id", required=True, help="支付单 ID（UI 发起支付后可见）")
    ap.add_argument(
        "--action",
        choices=[ACTION_SUCCESS, ACTION_FAIL],
        default=ACTION_SUCCESS,
        help="渠道通知的结果（默认 success）",
    )
    ap.add_argument("--tenant", default="0", help="X-Tenant-Id（默认 0）")
    ap.add_argument(
        "--tamper-signature",
        action="store_true",
        help="伪造签名——用于验证验签确实生效（期望 401）",
    )
    ap.add_argument(
        "--expect-status",
        default=None,
        help="回调后期望的支付单终态（默认 success→SUCCESS / fail→FAILED）",
    )
    args = ap.parse_args()

    expect_status = args.expect_status or (
        "SUCCESS" if args.action == ACTION_SUCCESS else "FAILED"
    )

    # 读端点需要凭证（模拟器只在"回调"这一跳扮演渠道，查询仍是普通用户行为）
    try:
        token = lib.login("admin", "123456", tenant=args.tenant)
    except SystemExit as e:
        print(f"[ERROR] 登录失败，无法读取支付单：{e}")
        return 1

    pid = args.payment_id.strip()
    st, before, raw = fetch_payment(pid, token, args.tenant)
    if st != 200 or before is None:
        print(f"[ERROR] 读取支付单失败：HTTP {st} {json.dumps(raw, ensure_ascii=False)[:300]}")
        return 1

    amount = before.get("amount")
    print(f"支付单 {pid}")
    print(f"  订单号    : {before.get('orderId')}")
    print(f"  金额      : {amount}")
    print(f"  渠道      : {before.get('channel')}")
    print(f"  当前状态  : {before.get('status')}")

    if before.get("status") in ("SUCCESS", "FAILED", "CLOSED", "REFUNDED"):
        print(
            f"[SKIP] 支付单已是终态 {before.get('status')}，渠道不会重复回调；"
            "如需重新演练请新发起一笔支付。"
        )
        return 0

    trade_no = f"SIM{int(time.time() * 1000)}"
    # lib.hmac_sign 要求 amount 为字符串（内部自行 stripTrailingZeros，与服务端口径一致）
    signature = lib.hmac_sign(pid, trade_no, str(amount))
    if args.tamper_signature:
        signature = "deadbeef" * 8

    body = {
        "paymentId": int(pid),
        "channelTradeNo": trade_no,
        "paidAmount": float(amount),
        "signature": signature,
        "success": args.action == ACTION_SUCCESS,
    }

    print(f"\n→ 渠道回调（无凭证，直打网关）：action={args.action} tradeNo={trade_no}")
    # 关键：token=None —— 真实渠道没有我方 JWT，必须走 permitAll 白名单
    st, j = lib.http(
        "POST", "/api/v1/payments/callback", body, token=None, tenant=args.tenant
    )
    print(f"  HTTP {st}  {json.dumps(j, ensure_ascii=False)[:280]}")

    if args.tamper_signature:
        ok = st == 401
        print(f"\n{'✅' if ok else '❌'} 伪造签名{'被拒绝（验签生效）' if ok else '竟被接受（验签失效！）'}")
        return 0 if ok else 1

    if st != 200:
        print(f"[ERROR] 回调被拒绝（HTTP {st}）。若返回 401，多半是网关未放通 callback 白名单。")
        return 1

    _, after, _ = fetch_payment(pid, token, args.tenant)
    actual = (after or {}).get("status")
    print(f"\n回调后状态：{before.get('status')} → {actual}（期望 {expect_status}）")
    if (after or {}).get("channelTradeNo"):
        print(f"渠道流水号：{after.get('channelTradeNo')}")

    ok = actual == expect_status
    print(f"{'✅' if ok else '❌'} 支付状态推进{'符合预期' if ok else '不符合预期'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
