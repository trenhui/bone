package com.bone.example.extension.payment;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.result.ValidationResult;

/**
 * 电商支付扩展实现
 */
public class EcommercePaymentExtension implements PaymentExtPoint {

    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        // 简化实现，直接返回成功
        return ValidationResult.success();
    }

    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        // 简化实现，返回null
        return null;
    }

    @Override
    public void postPayProcess(BizContext<PaymentResult> context) {
        // 简化实现，空方法
    }
}