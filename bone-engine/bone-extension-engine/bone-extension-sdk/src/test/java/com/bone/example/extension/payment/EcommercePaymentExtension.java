package com.bone.example.extension.payment;

import com.bone.engine.extension.annotation.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import com.bone.example.extension.result.ValidationResult;

/**
 * 电商支付扩展点实现类
 * <p>
 * 提供电商场景下的支付处理逻辑，包括支付金额计算、手续费应用等功能
 */
@Extension(
    name = "电商支付扩展实现",
    description = "处理电商场景下的支付请求，提供专业的电商支付计算逻辑",
    version = "1.0.0",
    priority = 20
)
@ExtensionDoc(
    description = "专为电商平台设计的支付扩展实现，提供符合电商场景特点的支付计算和处理功能",
    notes = "该扩展具有中等优先级(20)，确保在特定领域扩展之后但在默认实现之前执行\n使用说明：当支付请求来源于电商平台且商户ID以'ECOM'开头时自动应用此扩展\n最佳实践：\n1. 确保支付计算精确到小数点后两位\n2. 在处理大量订单时注意性能优化\n3. 关键计算结果应记录日志以便追踪"
)
@Component
public class EcommercePaymentExtension implements PaymentExtPoint {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(EcommercePaymentExtension.class);
    
    // 常量定义
    private static final String ECOMMERCE_PREFIX = "ECOM"; // 电商平台订单ID前缀
    private static final BigDecimal FEE_RATE = new BigDecimal("0.02"); // 电商场景手续费率2%
    private static final String DEFAULT_CURRENCY = "CNY"; // 默认货币类型

    /**
     * 支付前验证
     * <p>
     * 在支付前对请求进行验证，确保支付参数的有效性
     * 
     * @param context 支付上下文，包含支付请求信息
     * @return 验证结果
     */
    @Override
    public ValidationResult prePayValidate(final BizContext<PaymentTestRequest> context) {
        logger.info("开始执行电商支付前置验证");
        // 检查上下文是否有效
        if (context == null || context.getData() == null) {
            logger.warn("支付上下文或请求数据为空");
            return ValidationResult.fail("INVALID_CONTEXT", "支付上下文或请求数据不能为空");
        }
        
        PaymentTestRequest request = context.getData();
        
        // 验证订单ID是否为电商平台
        String orderId = request.getOrderId();
        if (orderId == null || !orderId.startsWith(ECOMMERCE_PREFIX)) {
            logger.warn("无效的电商平台订单ID: {}", orderId);
            return ValidationResult.fail("INVALID_ECOMMERCE_ORDER_ID", "无效的电商平台订单ID");
        }
        
        // 验证金额有效性
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.fail("INVALID_AMOUNT", "支付金额必须大于零");
        }
        
        // 所有验证通过
        logger.info("电商支付前置验证通过，订单ID: {}", orderId);
        return ValidationResult.success();
    }

    /**
     * 计算支付金额
     * <p>
     * 根据电商场景的业务规则计算最终支付金额，包括手续费等
     * 
     * @param context 包含支付请求信息的业务上下文
     * @return 支付计算结果对象，包含计算后的金额和明细
     */
    @Override
    public PaymentCalculationResult calculatePayment(final BizContext<PaymentTestRequest> context) {
        logger.info("开始计算电商支付金额");
        
        // 参数校验
        if (context == null || context.getData() == null) {
            logger.error("支付上下文或请求数据为空");
            throw new IllegalArgumentException("支付上下文或请求数据不能为空");
        }
        
        PaymentTestRequest request = context.getData();
        
        // 获取原始金额
        BigDecimal originalAmount = request.getAmount();
        
        // 计算电商场景下的手续费（商品总价的2%）
        BigDecimal feeAmount = originalAmount.multiply(FEE_RATE).setScale(2, java.math.RoundingMode.HALF_UP);
        
        // 计算最终支付金额
        BigDecimal finalAmount = originalAmount.add(feeAmount);
        
        logger.info("电商支付金额计算完成，订单ID: {}, 原始金额: {}, 手续费: {}, 最终金额: {}",
                request.getOrderId(), originalAmount, feeAmount, finalAmount);
        
        // 使用builder模式创建支付计算结果对象
        return PaymentCalculationResult.builder()
                .originalAmount(originalAmount)
                .finalAmount(finalAmount)
                .feeAmount(feeAmount)
                .taxAmount(BigDecimal.ZERO) // 电商场景暂不考虑税费
                .currency(DEFAULT_CURRENCY)
                .build();
    }

    /**
     * 支付后处理
     * <p>
     * 在支付完成后执行额外的处理逻辑，如订单更新、通知发送等
     * 
     * @param context 支付结果上下文
     */
    @Override
    public void postPayProcess(final BizContext<PaymentResult> context) {
        logger.info("开始执行电商支付后处理");
        
        // 参数校验
        if (context == null || context.getData() == null) {
            logger.warn("支付后处理上下文或数据为空");
            return;
        }
        
        PaymentResult paymentResult = context.getData();
        
        // 仅在支付成功时进行后续处理
        if (paymentResult.isSuccess()) {
            logger.info("电商支付成功，执行后续处理");
            // 实际应用中可以执行订单状态更新等操作
            // 例如：更新订单状态、发送通知等
        } else {
            logger.warn("电商支付失败，跳过后续处理");
        }
    }

    /**
     * 判断扩展点是否适用于当前请求
     * <p>
     * 根据商户ID判断是否为电商平台请求
     * 
     * @param context 业务上下文
     * @return 是否适用于电商场景
     */
    @Override
    public boolean isApplicable(final BizContext<?> context) {
        // 检查上下文是否有效
        if (context == null || !(context.getData() instanceof PaymentTestRequest)) {
            return false;
        }
        
        // 检查订单ID是否以电商平台前缀开头
        PaymentTestRequest request = (PaymentTestRequest) context.getData();
        boolean isApplicable = request.getOrderId() != null && 
               request.getOrderId().startsWith(ECOMMERCE_PREFIX);
        
        logger.debug("检查电商支付扩展适用性，订单ID: {}, 适用结果: {}", 
                request.getOrderId(), isApplicable);
        
        return isApplicable;
    }
    
    @Override
    public int getPriority() {
        // 中等优先级
        return 20;
    }
}