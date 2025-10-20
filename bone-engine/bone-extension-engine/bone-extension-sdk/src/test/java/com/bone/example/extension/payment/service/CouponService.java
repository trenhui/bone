package com.bone.example.extension.payment.service;

import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;

/**
 * 优惠券服务接口
 * 提供优惠券验证和折扣计算功能
 */
public interface CouponService {
    
    /**
     * 验证优惠券是否有效
     * @param couponId 优惠券ID
     * @param userId 用户ID
     * @param amount 订单金额
     * @return 验证结果
     */
    ValidationResult validateCoupon(String couponId, String userId, BigDecimal amount);
    
    /**
     * 计算优惠券折扣金额
     * @param couponId 优惠券ID
     * @param amount 订单金额
     * @return 折扣金额
     */
    BigDecimal calculateDiscount(String couponId, BigDecimal amount);
}