# -*- coding: utf-8 -*-
"""渠道回调签名（Mock 口径）。

与 `MockPaymentSignaturePortAdapter#MOCK_SECRET` 同密钥、同 payload 口径：
`paymentId|channelTradeNo|amount.stripTrailingZeros().toPlainString()`。
渠道模拟器等脚本持此密钥是合理的——真实渠道也持有自己的签名密钥，
密钥硬编码是 Mock 验签适配器的既有事实，本模块只是复刻，不新增风险。
"""
import hashlib
import hmac

# 渠道侧签名密钥，与 MockPaymentSignaturePortAdapter 保持一致。
MOCK_SECRET = b"bone-blueprint-mock-secret"


def hmac_sign(payment_id, trade_no, amount):
    """按服务端 verify() 的 payload 口径生成 HMAC-SHA256 签名。"""
    amt = str(amount)
    if "." in amt:
        amt = amt.rstrip("0").rstrip(".")
    payload = f"{payment_id}|{trade_no}|{amt}"
    return hmac.new(MOCK_SECRET, payload.encode(), hashlib.sha256).hexdigest()
