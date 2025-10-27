package com.bone.example.extension.payment;

import com.bone.example.extension.common.PaymentRequest;

/**
 * 支付请求测试类
 * <p>
 * 继承自通用PaymentRequest，添加特定的测试相关字段
 */
public class PaymentTestRequest extends PaymentRequest {
    private String couponId;
    private int pointsToDeduct;
    
    /**
     * 获取优惠券ID
     * 
     * @return 优惠券ID
     */
    public String getCouponId() {
        return couponId;
    }
    
    /**
     * 设置优惠券ID
     * 
     * @param couponId 优惠券ID
     */
    public void setCouponId(String couponId) {
        this.couponId = couponId;
    }
    
    /**
     * 获取要抵扣的积分
     * 
     * @return 要抵扣的积分
     */
    public int getPointsToDeduct() {
        return pointsToDeduct;
    }
    
    /**
     * 设置要抵扣的积分
     * 
     * @param pointsToDeduct 要抵扣的积分
     */
    public void setPointsToDeduct(int pointsToDeduct) {
        this.pointsToDeduct = pointsToDeduct;
    }
}