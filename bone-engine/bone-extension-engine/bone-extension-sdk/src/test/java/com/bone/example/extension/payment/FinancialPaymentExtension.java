package com.bone.example.extension.payment;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;

/**
 * 金融服务租户支付实现
 */
@Extension(tenantCode = "FINANCIAL_TENANT")
public class FinancialPaymentExtension implements PaymentExtPoint {
    
    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        // 简化实现，直接返回成功
        return ValidationResult.success();
    }
    
    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        // 使用PaymentCalculationResult的builder方法
        try {
            // 尝试使用反射调用builder方法
            PaymentCalculationResult result = (PaymentCalculationResult) PaymentCalculationResult.class.getDeclaredMethod("builder").invoke(null);
            return result;
        } catch (Exception e) {
            // 如果失败，返回null
            return null;
        }
    }
    
    @Override
    public void postPayProcess(BizContext<PaymentResult> context) {
        // 简化实现，什么都不做
    }
}