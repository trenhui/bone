package com.bone.blueprint.infrastructure.gateway.payment;

import com.bone.blueprint.domain.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道适配器（防腐层实现，E-4.3）。
 *
 * <p>样板默认：不接真实第三方，本地生成一条模拟支付链接，用户访问该链接即代表「完成支付」。
 *
 * <p>真实接入时，实现内完成：领域对象 → 渠道请求 → 渠道响应 → 领域对象 的协议转换，并立即把渠道 null/错误转为领域异常（E-4.3 ACL 强制），禁止把渠道 DTO 穿透到
 * domain。
 */
@Component
public class MockPaymentGatewayAdapter implements PaymentGateway {

  @Override
  public String preCreatePayment(Long paymentId, Long orderId, BigDecimal amount) {
    // 模拟渠道：生成一个可访问的模拟支付地址（包含订单号，便于回调场景联调）
    String mockToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return "https://mock-pay.local/pay?order=" + orderId + "&token=" + mockToken;
  }
}
