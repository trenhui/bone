package com.bone.blueprint.infrastructure.gateway.payment;

import com.bone.blueprint.application.port.out.PaymentSignaturePort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道回调验签（防腐层实现，§19）。
 *
 * <p>用固定共享密钥做 HMAC-SHA256，对 {@code paymentId|channelTradeNo|amount} 计算签名并与请求签名比对， 演示
 * 「回调先验签再进应用层」。密钥仅存于 infrastructure（模拟常量），真实接入用配置中心/密钥管理，且用 {@link javax.crypto.Mac#equals}
 * 或常量时间比较防时序侧信道。
 *
 * <p><b>签名字段取自回调报文</b>（而非数据库里的支付单）：真实渠道也是对报文签名，adapter 才能在不查库的前提下 完成验签；金额统一 {@code
 * stripTrailingZeros} 后再签，避免 {@code 200} 与 {@code 200.00} 两种字符串导致签名不一致。
 */
@Component
public class SimulatedPaymentSignatureVerifier implements PaymentSignaturePort {

  /** 模拟共享密钥（仅演示；真实接入须外部化）。 */
  private static final String MOCK_SECRET = "bone-blueprint-mock-secret";

  @Override
  public boolean verify(
      long paymentId, String channelTradeNo, BigDecimal amount, String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    if (amount == null || channelTradeNo == null) {
      // 报文缺字段：不足以计算签名，按不可信处理（不抛异常，由调用方决定拒绝语义）
      return false;
    }
    String expected = hmac(paymentId, channelTradeNo, amount);
    // 常量时间比较，防时序侧信道
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * 生成模拟签名（仅供契约测试 / 联调构造签名；生产由渠道侧签名）。
   *
   * <p>与 {@link #verify} 共用同一 payload 与 HMAC 实现，避免「签名与验签两套口径」。
   */
  public String sign(long paymentId, String channelTradeNo, BigDecimal amount) {
    return hmac(paymentId, channelTradeNo, amount);
  }

  private String hmac(long paymentId, String channelTradeNo, BigDecimal amount) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(MOCK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw =
          mac.doFinal(payload(paymentId, channelTradeNo, amount).getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : raw) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("HMAC 计算失败", e);
    }
  }

  private static String payload(long paymentId, String channelTradeNo, BigDecimal amount) {
    return paymentId + "|" + channelTradeNo + "|" + amount.stripTrailingZeros().toPlainString();
  }
}
