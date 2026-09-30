package com.bone.blueprint.infrastructure.gateway.payment;

import com.bone.blueprint.domain.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道适配器（防腐层实现，E-4.3）。
 *
 * <p>样板默认：不接真实第三方，本地生成一条模拟支付链接，用户访问该链接即代表「完成支付」。
 *
 * <p>真实接入时，实现内完成：领域对象 → 渠道请求 → 渠道响应 → 领域对象 的协议转换，并立即把渠道 null/错误转为领域异常（E-4.3 ACL 强制），禁止把渠道 DTO 穿透到
 * domain。
 *
 * <p><b>启动告警</b>：本类是<strong>占位实现</strong>——不接真实支付渠道，仅生成可访问的本地 mock 链接。prod profile 下直接阻断启动（与 {@link
 * com.bone.blueprint.infrastructure.signature.MockPaymentSignaturePortAdapter} 同模式）。
 */
@Slf4j
@Component
public class MockPaymentGatewayAdapter
    implements PaymentGateway, EnvironmentAware, InitializingBean {

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    boolean prod = environment != null && environment.acceptsProfiles("prod");
    String message = "支付渠道当前装配的是 Mock 实现（仅生成本地 mock-pay 链接，未对接真实渠道）；生产环境必须替换为真实支付网关";
    if (prod) {
      throw new IllegalStateException("[配置事故] " + message + " — 已阻断 prod 启动");
    } else {
      log.warn("{}", message);
    }
  }

  @Override
  public String preCreatePayment(Long paymentId, Long orderId, BigDecimal amount) {
    // 模拟渠道：生成一个可访问的模拟支付地址（包含订单号，便于回调场景联调）
    String mockToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return "https://mock-pay.local/pay?order=" + orderId + "&token=" + mockToken;
  }
}
