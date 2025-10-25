package com.bone.example.extension.payment.service;

import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;

/**
 * 优惠券服务接口
 * <p>
 * 定义优惠券相关的核心业务操作，包括优惠券验证和折扣计算功能。
 * 所有实现类应确保财务计算的精确性，并提供严格的参数验证。
 */
public interface CouponService {
    
    /**
     * 验证优惠券使用条件
     * <p>
     * 检查优惠券是否满足使用条件，包括但不限于：
     * <ul>
     *   <li>优惠券是否存在</li>
     *   <li>是否在有效期内</li>
     *   <li>是否适用于当前用户</li>
     *   <li>订单金额是否达到最低消费门槛</li>
     * </ul>
     * 
     * @param couponId 优惠券ID，不能为空或空白
     * @param userId 用户ID，不能为空或空白
     * @param amount 订单金额，必须大于等于零
     * @return 验证结果对象，包含验证状态和详细错误信息（如有）
     * @throws IllegalArgumentException 当输入参数不合法时抛出明确的错误信息
     */
    ValidationResult validateCoupon(final String couponId, final String userId, final BigDecimal amount);
    
    /**
     * 计算优惠券实际折扣金额
     * <p>
     * 根据优惠券类型、规则和订单金额精确计算可享受的折扣金额。
     * 实现类应确保返回的折扣金额不会超过订单总额，并保持适当的精度。
     * 
     * @param couponId 优惠券ID，不能为空或空白
     * @param amount 订单金额，必须大于零
     * @return 精确计算的折扣金额，非null且大于等于零，通常保留2位小数
     * @throws IllegalArgumentException 当优惠券无效、不可用或参数不合法时抛出明确的错误信息
     */
    BigDecimal calculateDiscount(final String couponId, final BigDecimal amount);
}