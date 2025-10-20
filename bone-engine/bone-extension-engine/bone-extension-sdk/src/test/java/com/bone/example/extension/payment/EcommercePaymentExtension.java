package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.payment.exception.PaymentException;
import com.bone.example.extension.payment.service.CouponService;
import com.bone.example.extension.payment.service.DefaultCouponService;
import com.bone.example.extension.payment.service.DefaultPointsService;
import com.bone.example.extension.payment.service.DefaultPaymentLogService;
import com.bone.example.extension.payment.service.PointsService;
import com.bone.example.extension.payment.service.PaymentLogService;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 电商平台租户支付实现
 * 提供标准电商支付流程，包括优惠券、积分抵扣等功能
 */
@Extension(tenantCode = "ECOMMERCE_TENANT")
@Component
@Slf4j
public class EcommercePaymentExtension implements PaymentExtPoint {
    
    // 常量定义
    private static final String CURRENCY_CNY = "CNY";
    private static final String COUPON_DISCOUNT_KEY = "COUPON_DISCOUNT";
    private static final String POINTS_DEDUCTION_KEY = "POINTS_DEDUCTION";
    
    // 错误码常量
    private static final String INVALID_REQUEST_CODE = "INVALID_REQUEST";
    private static final String INVALID_REQUEST_MSG = "支付请求参数不完整";
    private static final String INVALID_ORDER_CODE = "INVALID_ORDER";
    private static final String INVALID_ORDER_MSG = "订单无效或已被处理";
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String VALIDATION_ERROR_MSG = "支付验证过程中出现异常";
    
    // 使用依赖注入替代内部类
    @Autowired
    private CouponService couponService;
    
    @Autowired
    private PointsService pointsService;
    
    @Autowired
    private PaymentLogService logService;
    
    // 构造函数用于测试，允许手动注入依赖
    public EcommercePaymentExtension() {
        // 当Spring容器未初始化时使用默认实现
        this.couponService = new DefaultCouponService();
        this.pointsService = new DefaultPointsService();
        this.logService = new DefaultPaymentLogService();
    }
    
    // 允许手动设置依赖，便于测试
    public void setCouponService(CouponService couponService) {
        this.couponService = couponService;
    }
    
    public void setPointsService(PointsService pointsService) {
        this.pointsService = pointsService;
    }
    
    public void setLogService(PaymentLogService logService) {
        this.logService = logService;
    }
    
    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        try {
            PaymentRequest request = context.getData();
            
            // 参数验证
            if (request == null || request.getOrderId() == null) {
                throw new PaymentException(INVALID_REQUEST_CODE, INVALID_REQUEST_MSG);
            }
            
            // 业务验证
            if (!isOrderValid(request.getOrderId(), context.getTenantCode())) {
                throw new PaymentException(INVALID_ORDER_CODE, INVALID_ORDER_MSG);
            }
            
            // 优惠券验证
            if (request.getCouponId() != null && !request.getCouponId().isEmpty()) {
                ValidationResult couponValidation = couponService.validateCoupon(
                    request.getCouponId(), request.getUserId(), request.getAmount());
                if (!couponValidation.isSuccess()) {
                    // 优惠券验证失败，可以直接返回结果或抛出异常
                    // 这里选择直接返回，因为优惠券验证可能有多种业务规则
                    return couponValidation;
                }
            }
            
            log.info("Payment validation passed for order: {}", request.getOrderId());
            return ValidationResult.success();
        } catch (PaymentException e) {
            log.warn("Payment validation failed: {}, message: {}", e.getErrorCode(), e.getMessage());
            return ValidationResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during payment validation for tenant: {}", 
                     context.getTenantCode(), e);
            return ValidationResult.fail(VALIDATION_ERROR_CODE, VALIDATION_ERROR_MSG);
        }
    }
    
    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        BigDecimal baseAmount = request.getAmount();
        BigDecimal finalAmount = baseAmount;
        Map<String, BigDecimal> deductionDetails = new HashMap<>();
        
        // 优惠券计算
        if (request.getCouponId() != null && !request.getCouponId().isEmpty()) {
            BigDecimal couponDiscount = couponService.calculateDiscount(
                request.getCouponId(), baseAmount);
            finalAmount = finalAmount.subtract(couponDiscount);
            deductionDetails.put(COUPON_DISCOUNT_KEY, couponDiscount);
        }
        
        // 3. 应用积分抵扣
        if (request.getPointsToDeduct() > 0) {
            BigDecimal pointsValue = pointsService.calculatePointsValue(request.getPointsToDeduct());
            finalAmount = finalAmount.subtract(pointsValue);
            deductionDetails.put(POINTS_DEDUCTION_KEY, pointsValue);
        }
        
        // 确保最终金额不为负数
        finalAmount = finalAmount.max(BigDecimal.ZERO);
        
        return PaymentCalculationResult.builder()
            .originalAmount(baseAmount)
            .finalAmount(finalAmount)
            .deductionDetails(deductionDetails)
            .currency(CURRENCY_CNY)
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
    
    // 内部服务实现类已移至独立的服务接口和实现类中
}