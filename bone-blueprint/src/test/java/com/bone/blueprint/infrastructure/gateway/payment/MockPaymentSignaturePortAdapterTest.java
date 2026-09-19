package com.bone.blueprint.infrastructure.gateway.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 回调验签 ACL 契约测试（E-10：外部系统集成必须有契约测试）。
 *
 * <p>验证「签名/验签口径一致」与「不可信输入一律拒绝」两类语义：验签是资金链路的入口闸门， 若签名口径（字段顺序、金额格式）两侧不一致，或空值/篡改被放行，伪造回调即可改变支付单终态。
 */
class MockPaymentSignaturePortAdapterTest {

  private final MockPaymentSignaturePortAdapter verifier = new MockPaymentSignaturePortAdapter();

  @Test
  void testValidSignatureAccepted() {
    String sign = verifier.sign(1L, "trade-001", new BigDecimal("200"));
    assertTrue(verifier.verify(1L, "trade-001", new BigDecimal("200"), sign));
  }

  @Test
  void testTamperedAmountRejected() {
    String sign = verifier.sign(1L, "trade-001", new BigDecimal("200"));
    // 篡改金额：签名不变 → 必须拒绝
    assertFalse(verifier.verify(1L, "trade-001", new BigDecimal("199"), sign));
  }

  @Test
  void testTamperedTradeNoRejected() {
    String sign = verifier.sign(1L, "trade-001", new BigDecimal("200"));
    assertFalse(verifier.verify(1L, "trade-002", new BigDecimal("200"), sign));
  }

  @Test
  void testBlankSignatureRejected() {
    assertFalse(verifier.verify(1L, "trade-001", new BigDecimal("200"), null));
    assertFalse(verifier.verify(1L, "trade-001", new BigDecimal("200"), "  "));
  }

  @Test
  void testMissingPayloadFieldRejected() {
    String sign = verifier.sign(1L, "trade-001", new BigDecimal("200"));
    // 报文缺字段：无法计算签名 → 按不可信处理（不抛异常）
    assertFalse(verifier.verify(1L, null, new BigDecimal("200"), sign));
    assertFalse(verifier.verify(1L, "trade-001", null, sign));
  }

  @Test
  void testAmountScaleDoesNotAffectSignature() {
    // 金额等价形式（200 与 200.00）必须得到同一签名，否则渠道/我方序列化差异会造成大量误拒
    String sign = verifier.sign(1L, "trade-001", new BigDecimal("200"));
    assertTrue(verifier.verify(1L, "trade-001", new BigDecimal("200.00"), sign));
  }
}
