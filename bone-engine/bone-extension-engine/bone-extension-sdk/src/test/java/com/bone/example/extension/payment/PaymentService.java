package com.bone.example.extension.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

// 显式导入test包中的PaymentRequest类，避免与main包中的类冲突
// PaymentRequest类在同一个包中

/**
 * 支付服务实现类
 * <p>
 * 封装支付处理的核心业务逻辑，协调支付扩展点的调用，提供统一的支付处理入口。
 * 负责参数验证、扩展点路由、异常处理等核心功能。
 */
public class PaymentService {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    
    // 支付扩展点实现
    private PaymentExtPoint paymentExtPoint;

    /**
     * 处理支付请求
     * <p>
     * 处理支付请求的主流程，包括参数验证、扩展点路由和异常处理。
     * 根据租户代码选择对应的处理逻辑，并确保返回统一格式的支付结果。
     * 
     * @param request 支付请求对象，包含支付相关信息，非null
     * @param tenantCode 租户代码，用于区分不同租户的处理逻辑，非null且非空
     * @return 支付处理结果对象，包含交易状态和相关信息
     * @throws IllegalArgumentException 当请求参数不合法时抛出明确的错误信息
     * @throws RuntimeException 当支付处理过程中发生异常时抛出并包含原始异常信息
     */
    public Object processPayment(final PaymentTestRequest request, final String tenantCode) {
        // 参数校验
        validateRequest(request, tenantCode);
        
        try {
            logger.info("开始处理支付请求，租户代码: {}, 请求信息: {}", tenantCode, request);
            
            // 检查扩展点是否已设置
            if (paymentExtPoint == null) {
                logger.warn("支付扩展点未设置，使用默认处理逻辑");
                return createDefaultPaymentResult(request);
            }
            
            // 实际应用中这里会根据租户代码获取对应的扩展实现
            // 并调用相应的业务逻辑处理支付请求
            
            // 简化实现，创建并返回默认支付结果
            final Object result = createDefaultPaymentResult(request);
            logger.debug("支付请求处理完成，租户代码: {}, 处理结果: {}", tenantCode, result);
            return result;
        } catch (final IllegalArgumentException e) {
            // 参数验证错误，保持原样抛出
            logger.warn("支付请求参数验证失败，租户代码: {}", tenantCode, e);
            throw e;
        } catch (final Exception e) {
            logger.error("支付请求处理异常，租户代码: {}", tenantCode, e);
            throw new RuntimeException("支付处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建默认支付结果
     * <p>
     * 当扩展点未设置或需要返回默认结果时使用
     * 
     * @param request 支付请求对象
     * @return 默认的支付结果对象
     */
    private Object createDefaultPaymentResult(final PaymentTestRequest request) {
        // 返回一个简单的Map作为结果
        Map<String, Object> result = new HashMap<>();
        result.put("userId", request.getUserId());
        result.put("transactionId", "DEFAULT-" + System.currentTimeMillis());
        result.put("success", true);
        return result;
    }
    
    /**
     * 设置支付扩展点
     * <p>
     * 用于注入支付扩展点实现
     * 
     * @param paymentExtPoint 支付扩展点实现
     */
    public void setPaymentExtPoint(final PaymentExtPoint paymentExtPoint) {
        this.paymentExtPoint = paymentExtPoint;
    }
    
    /**
     * 获取支付扩展点
     * 
     * @return 当前设置的支付扩展点实现
     */
    public PaymentExtPoint getPaymentExtPoint() {
        return paymentExtPoint;
    }
    
    /**
     * 参数验证
     * <p>
     * 验证支付请求参数的合法性，包括请求对象、租户代码和金额的验证
     * 
     * @param request 支付请求对象
     * @param tenantCode 租户代码
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    private void validateRequest(final PaymentTestRequest request, final String tenantCode) {
        if (request == null) {
            throw new IllegalArgumentException("支付请求不能为空");
        }
        
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("租户代码不能为空");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("支付金额必须大于零");
        }
        
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        if (request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
    }
}