package com.bone.example.extension.payment.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/**
 * 默认积分服务实现
 */
@Service
public class DefaultPointsService implements PointsService {
    
    private static final String POINTS_VALUE_RATE = "0.01"; // 1积分=0.01元
    
    @Override
    public BigDecimal calculatePointsValue(int points) {
        // 计算积分的货币价值
        // 实际应用中可能需要根据会员等级、活动等因素调整兑换比例
        return new BigDecimal(points).multiply(new BigDecimal(POINTS_VALUE_RATE));
    }
}