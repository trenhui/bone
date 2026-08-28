package com.bone.blueprint.infrastructure.gateway.payment;

import com.bone.blueprint.domain.gateway.PaymentSignaturePort;
import com.bone.blueprint.domain.payment.Payment;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道回调验签（防腐层实现，§19）。
 *
 * <p>用固定共享密钥做 HMAC-SHA256，对 {@code paymentId|orderId|amount} 计算签名并与请求签名比对，演示 「回调先验签再进领域」。密钥仅存于
 * infrastructure（模拟常量），真实接入用配置中心/密钥管理，且用 {@link javax.crypto.Mac#equals} 或常量时间比较防时序侧信道。
 */
@Component
public class SimulatedPaymentSignatureVerifier implements PaymentSignaturePort {

  /** 模拟共享密钥（仅演示；真实接入须外部化）。 */
  private static final String MOCK_SECRET = "bone-blueprint-mock-secret";

  @Override
  public boolean verify(Payment payment, BigDecimal paidAmount, String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    String expected = hmac(payment.getId(), payment.getOrderId(), payment.getAmount());
    // 常量时间比较，防时序侧信道
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
  }

  private String hmac(Long paymentId, Long orderId, BigDecimal amount) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(MOCK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String payload = paymentId + "|" + orderId + "|" + amount;
      byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : raw) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("HMAC 计算失败", e);
    }
  }
}
