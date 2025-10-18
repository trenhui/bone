package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.example.extension.result.ValidationResult;

/**
 * 支付处理扩展点
 * 定义了支付前验证、支付金额计算和支付后处理三个核心方法
 */
@ExtPoint(name = "支付处理扩展点", description = "支持多租户的支付处理流程定制")
public interface PaymentExtPoint {
    
    /**
     * 支付前验证
     * @param context 业务上下文，包含订单信息和支付请求
     * @return 验证结果，包含是否通过和错误信息
     */
    ValidationResult prePayValidate(BizContext<PaymentRequest> context);
    
    /**
     * 计算最终支付金额
     * @param context 业务上下文
     * @return 支付计算结果
     */
    PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context);
    
    /**
     * 支付后处理
     * @param context 业务上下文，包含支付结果
     */
    void postPayProcess(BizContext<PaymentResult> context);
}