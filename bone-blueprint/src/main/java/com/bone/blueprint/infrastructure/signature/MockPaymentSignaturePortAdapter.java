package com.bone.blueprint.infrastructure.signature;

import com.bone.blueprint.application.port.out.PaymentSignaturePort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道回调验签（防腐层实现，E-4.3）。
 *
 * <p><b>为何落在 {@code infrastructure/signature} 而不是 {@code infrastructure/gateway}</b>：E-10.2
 * 的决策树按<strong>端口类型</strong>分落点 ——{@code domain/gateway} 的业务网关实现落 {@code
 * infrastructure/gateway}，而本类实现的是 {@code application/port/out}
 * 的技术端口（验签是应用流程需要的能力，不是「领域规则依赖的外部业务事实」），按决策树落 {@code infrastructure/{具体能力}}。验证能力归到签名能力包，与 {@code
 * infrastructure/security}（用户态 JWT 鉴权）区分开： 前者是<strong>外部回调报文</strong>的验签，后者是入站请求的身份认证。
 *
 * <p>用固定共享密钥做 HMAC-SHA256，对 {@code paymentId|channelTradeNo|amount} 计算签名并与请求签名比对， 演示
 * 「回调先验签再进应用层」。密钥仅存于 infrastructure（模拟常量），真实接入用配置中心/密钥管理，比较用 {@link
 * java.security.MessageDigest#isEqual} 做常量时间比较防时序侧信道。
 *
 * <p><b>签名字段取自回调报文</b>（而非数据库里的支付单）：真实渠道也是对报文签名，adapter 才能在不查库的前提下 完成验签；金额统一 {@code
 * stripTrailingZeros} 后再签，避免 {@code 200} 与 {@code 200.00} 两种字符串导致签名不一致。
 *
 * <p><b>启动告警</b>：本类是<strong>占位实现</strong>——共享密钥硬编码在源码里。它一旦装配进 prod 运行时就是配置事故
 * （任何人拿到源码即可伪造回调、改变支付单终态），故启动时按 profile 分级留痕（prod 为 {@code ERROR}，其余 {@code
 * WARN}）；真实接入必须把密钥外部化到配置中心/密钥管理，并换成渠道侧真实验签实现。
 */
@Slf4j
@Component
public class MockPaymentSignaturePortAdapter
    implements PaymentSignaturePort, EnvironmentAware, InitializingBean {

  /** 模拟共享密钥（仅演示；真实接入须外部化）。 */
  private static final String MOCK_SECRET = "bone-blueprint-mock-secret";

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  /** 启动期自检：Mock 验签被装配即告警，prod profile 下升级为 ERROR。 */
  @Override
  public void afterPropertiesSet() {
    // 单测直接 new 本类时 environment 为 null（不经过 Spring 生命周期），静默跳过即可。
    boolean prod = environment != null && environment.acceptsProfiles("prod");
    String message =
        "支付回调验签当前装配的是 Mock 实现（共享密钥硬编码在源码中，仅用于演示/本地联调）；" + "生产环境必须外部化密钥并接入渠道真实验签，否则任何持有源码者都可伪造回调";
    if (prod) {
      log.error("[配置事故] {}", message);
    } else {
      log.warn("{}", message);
    }
  }

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
