package com.bone.example.extension.payment;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;
import com.bone.example.extension.result.ValidationResult;

/**
 * 支付处理扩展点
 * 定义了支付前验证、支付金额计算和支付后处理三个核心方法
 */
@ExtPoint(
    name = "支付扩展点",
    description = "处理各种支付方式的扩展点接口",
    domain = "支付系统",
    category = "支付处理",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = false,
    timeout = 5000
)
@ExtPointDoc(
    description = "该扩展点用于处理不同支付方式的支付请求，支持多种支付渠道。",
    usage = "1. 在交易完成后调用进行支付处理\n2. 根据支付类型和渠道选择合适的实现\n3. 处理支付状态回调",
    bestPractices = "1. 确保支付处理的幂等性\n2. 考虑事务一致性\n3. 实现超时和重试机制",
    notes = "支付处理的核心扩展点，需要保证高可靠性和安全性"
)
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