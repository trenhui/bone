package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 电商平台租户支付实现
 * 提供标准电商支付流程，包括优惠券、积分抵扣等功能
 */
@Extension(tenantCode = "ECOMMERCE_TENANT", priority = 100)
@Slf4j
public class EcommercePaymentExtension implements PaymentExtPoint {
    
    // 为了演示，这里模拟服务依赖
    private final CouponService couponService = new CouponService();
    private final PointsService pointsService = new PointsService();
    private final PaymentLogService logService = new PaymentLogService();
    
    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        try {
            PaymentRequest request = context.getData();
            
            // 参数验证
            if (request == null || request.getOrderId() == null) {
                return ValidationResult.fail("INVALID_REQUEST", "支付请求参数不完整");
            }
            
            // 业务验证
            if (!isOrderValid(request.getOrderId(), context.getTenantCode())) {
                return ValidationResult.fail("INVALID_ORDER", "订单无效或已被处理");
            }
            
            // 优惠券验证
            if (request.getCouponId() != null) {
                ValidationResult couponValidation = couponService.validateCoupon(
                    request.getCouponId(), request.getUserId(), request.getAmount());
                if (!couponValidation.isSuccess()) {
                    return couponValidation;
                }
            }
            
            log.info("Payment validation passed for order: {}", request.getOrderId());
            return ValidationResult.success();
        } catch (Exception e) {
            log.error("Payment validation failed for tenant: {}", context.getTenantCode(), e);
            return ValidationResult.fail("VALIDATION_ERROR", "支付验证过程中出现异常");
        }
    }
    
    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        BigDecimal baseAmount = request.getAmount();
        BigDecimal finalAmount = baseAmount;
        Map<String, BigDecimal> deductionDetails = new HashMap<>();
        
        // 优惠券计算
        if (request.getCouponId() != null) {
            BigDecimal couponDiscount = couponService.calculateDiscount(
                request.getCouponId(), baseAmount);
            finalAmount = finalAmount.subtract(couponDiscount);
            deductionDetails.put("COUPON_DISCOUNT", couponDiscount);
        }
        
        // 积分抵扣
        if (request.getPointsToDeduct() > 0) {
            BigDecimal pointsValue = pointsService.calculatePointsValue(request.getPointsToDeduct());
            finalAmount = finalAmount.subtract(pointsValue);
            deductionDetails.put("POINTS_DEDUCTION", pointsValue);
        }
        
        // 确保最终金额不为负数
        finalAmount = finalAmount.max(BigDecimal.ZERO);
        
        return PaymentCalculationResult.builder()
            .originalAmount(baseAmount)
            .finalAmount(finalAmount)
            .deductionDetails(deductionDetails)
            .currency("CNY")
            .build();
    }
    
    @Override
    public void postPayProcess(BizContext<PaymentResult> context) {
        PaymentResult result = context.getData();
        
        // 记录支付日志
        logService.logPayment(result);
        
        // 异步处理积分扣减（模拟）
        if (result.getPointsDeducted() > 0) {
            log.info("Async processing points deduction for user: {}", result.getUserId());
            // 实际项目中这里会使用线程池或消息队列进行异步处理
        }
        
        // 异步发送通知
        sendPaymentNotification(result);
    }
    
    private boolean isOrderValid(String orderId, String tenantCode) {
        // 实际的订单验证逻辑
        return true;
    }
    
    private void sendPaymentNotification(PaymentResult result) {
        // 发送支付通知的逻辑
        log.info("Sending payment notification for transaction: {}", result.getTransactionId());
    }
    
    // 模拟服务类
    static class CouponService {
        public ValidationResult validateCoupon(String couponId, String userId, BigDecimal amount) {
            // 模拟优惠券验证
            return ValidationResult.success();
        }
        
        public BigDecimal calculateDiscount(String couponId, BigDecimal amount) {
            // 模拟计算折扣
            return new BigDecimal("10.00");
        }
    }
    
    static class PointsService {
        public BigDecimal calculatePointsValue(int points) {
            // 模拟积分价值计算
            return new BigDecimal(points).multiply(new BigDecimal("0.01"));
        }
    }
    
    static class PaymentLogService {
        public void logPayment(PaymentResult result) {
            // 模拟记录支付日志
            log.info("Logging payment: {}", result.getTransactionId());
        }
    }
}