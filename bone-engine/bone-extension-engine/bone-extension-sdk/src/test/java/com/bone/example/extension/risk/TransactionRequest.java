package com.bone.example.extension.risk;

import java.math.BigDecimal;

/**
 * 交易请求接口
 * <p>
 * 定义了风控评估所需的交易基本信息，包括交易ID、用户ID、交易金额、交易类型等。
 * 所有风控规则实现都通过此接口获取交易数据进行风险评估。
 */
public interface TransactionRequest {
    
    /**
     * 获取交易ID
     * 
     * @return 交易唯一标识符，非空
     */
    String getTransactionId();
    
    /**
     * 获取用户ID
     * 
     * @return 用户唯一标识符，非空
     */
    String getUserId();
    
    /**
     * 获取交易金额
     * <p>
     * 使用BigDecimal确保金额计算精度
     * 
     * @return 交易金额，非空且大于0
     */
    BigDecimal getAmount();
    
    /**
     * 获取交易类型
     * 
     * @return 交易类型标识，如PAYMENT、REFUND、TRANSFER等，非空
     */
    String getTransactionType();
    
    /**
     * 获取交易时间戳
     * 
     * @return 交易发生的时间戳，格式为ISO-8601，非空
     */
    String getTimestamp();
    
    /**
     * 获取交易位置信息
     * 
     * @return 交易发生的地理位置，可能为null
     */
    String getLocation();
    
    /**
     * 获取设备信息
     * 
     * @return 用户设备标识信息，可能为null
     */
    String getDeviceInfo();
    
    /**
     * 获取IP地址
     * 
     * @return 用户IP地址，可能为null
     */
    String getIpAddress();
}