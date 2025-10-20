package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtensionContextManager;
import com.bone.engine.extension.ExtensionScopeUtil;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 支付服务
 * 负责协调支付流程，使用扩展点机制实现多租户支付处理差异化
 */
@Slf4j
public class PaymentService {

    @Autowired
    private PaymentExtPoint paymentExtPoint;

    // 用于测试的setter方法
    public void setPaymentExtPoint(PaymentExtPoint paymentExtPoint) {
        this.paymentExtPoint = paymentExtPoint;
    }

    /**
     * 处理支付请求
     *
     * @param request    支付请求
     * @param tenantCode 租户代码
     * @return 支付结果
     */
    public PaymentResult processPayment(PaymentRequest request, String tenantCode) {
        // 使用ExtensionScopeUtil的executeWithContext方法简化上下文管理和业务逻辑执行
        return ExtensionScopeUtil.executeWithContext(tenantCode, request, context -> {
            // 1. 支付前验证
            ValidationResult validation = paymentExtPoint.prePayValidate(context);
            if (!validation.isSuccess()) {
                log.warn("Payment validation failed for tenant {}: {}", tenantCode, validation.getErrorCode());
                return buildValidationFailedResult(request, validation);
            }

            // 2. 计算支付金额
            PaymentCalculationResult calculationResult = paymentExtPoint.calculatePayment(context);
            log.info("Payment calculated for user {}, final amount: {}", request.getUserId(), calculationResult.getFinalAmount());

            // 3. 创建支付结果，从上下文中获取事务ID
            String transactionId = (String) context.getAttribute(ExtensionScopeUtil.ATTR_TRANSACTION_ID);
            PaymentResult result = buildPaymentResult(request, calculationResult, transactionId);

            // 支付后处理
            BizContext<PaymentResult> resultContext = ExtensionContextManager.fromData(result);
            paymentExtPoint.postPayProcess(resultContext);

            return result;
        });
    }

    /**
     * 构建验证失败的支付结果
     */
    private PaymentResult buildValidationFailedResult(PaymentRequest request, ValidationResult validation) {
        PaymentResult result = new PaymentResult();
        result.setSuccess(false);
        result.setErrorCode(validation.getErrorCode());
        result.setErrorMessage(validation.getErrorMessage());
        result.setUserId(request.getUserId());
        return result;
    }

    /**
     * 构建支付成功结果
     */
    private PaymentResult buildPaymentResult(PaymentRequest request, PaymentCalculationResult calculationResult, String transactionId) {
        PaymentResult result = new PaymentResult();
        result.setTransactionId(transactionId);
        result.setSuccess(true);
        result.setFinalAmount(calculationResult.getFinalAmount());
        result.setUserId(request.getUserId());
        result.setPointsDeducted(request.getPointsToDeduct() > 0 ? request.getPointsToDeduct() : 0);
        return result;
    }
}