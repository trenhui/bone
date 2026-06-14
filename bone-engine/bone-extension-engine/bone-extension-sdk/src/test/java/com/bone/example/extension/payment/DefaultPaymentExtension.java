package com.bone.example.extension.payment;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension(name = "默认支付实现")
public class DefaultPaymentExtension implements PaymentExtPoint {

  private static final Logger log = LoggerFactory.getLogger(DefaultPaymentExtension.class);

  @Override
  public ValidationResult prePayValidate(BizContext<PaymentTestRequest> context) {
    log.info("走默认支付校验逻辑，tenant={}", context.getTenant());
    return ValidationResult.success();
  }

  @Override
  public PaymentCalculationResult calculatePayment(BizContext<PaymentTestRequest> context) {
    PaymentTestRequest request = context.getData();
    return PaymentCalculationResult.builder()
        .originalAmount(request.getAmount())
        .finalAmount(request.getAmount())
        .feeAmount(BigDecimal.ZERO)
        .currency("CNY")
        .build();
  }

  @Override
  public void postPayProcess(BizContext<PaymentResult> context) {
    log.info("默认支付后处理，tenant={}", context.getTenant());
  }
}
