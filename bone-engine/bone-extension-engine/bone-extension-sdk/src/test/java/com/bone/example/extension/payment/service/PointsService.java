package com.bone.example.extension.payment.service;

import java.math.BigDecimal;

/**
 * 积分服务接口
 * 提供积分价值计算功能
 */
public interface PointsService {
    
    /**
     * 计算积分的货币价值
     * @param points 积分数量
     * @return 积分对应的货币价值
     */
    BigDecimal calculatePointsValue(int points);
}