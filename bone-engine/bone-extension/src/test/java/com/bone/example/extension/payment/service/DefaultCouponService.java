package com.bone.example.extension.payment.service;

import com.bone.example.extension.result.ValidationResult;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * 默认优惠券服务实现
 */
@Service
public class DefaultCouponService implements CouponService {
    
    private static final String DEFAULT_COUPON_DISCOUNT = "10.00";
    
    @Override
    public ValidationResult validateCoupon(String couponId, String userId, BigDecimal amount) {
        // 模拟优惠券验证逻辑
        // 实际应用中应该调用数据库或缓存进行验证
        return ValidationResult.success();
    }
    
    @Override
    public BigDecimal calculateDiscount(String couponId, BigDecimal amount) {
        // 模拟计算折扣
        // 实际应用中应该根据优惠券类型和规则计算折扣
        return new BigDecimal(DEFAULT_COUPON_DISCOUNT);
    }
}