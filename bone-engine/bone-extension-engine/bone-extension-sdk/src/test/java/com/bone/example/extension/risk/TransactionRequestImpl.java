package com.bone.example.extension.risk;

import java.math.BigDecimal;

/**
 * 交易请求实现类
 * <p>
 * 实现了TransactionRequest接口，提供了交易相关的完整信息。
 * 除了接口定义的基本字段外，还包含了额外的交易详情信息。
 */
public class TransactionRequestImpl implements TransactionRequest {
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String transactionType;
    private String timestamp;
    private String location;
    private String deviceInfo;
    private String ipAddress;
    
    // 额外字段
    private String paymentMethod;
    private String merchantId;
    private String productCategory;
    
    /**
     * 创建构建器实例
     * @return TransactionRequestImpl构建器
     */
    public static TransactionRequestImplBuilder builder() {
        return new TransactionRequestImplBuilder();
    }
    
    /**
     * TransactionRequestImpl构建器类
     */
    public static class TransactionRequestImplBuilder {
        private String transactionId;
        private String userId;
        private BigDecimal amount;
        private String transactionType;
        private String timestamp;
        private String location;
        private String deviceInfo;
        private String ipAddress;
        private String paymentMethod;
        private String merchantId;
        private String productCategory;
        
        public TransactionRequestImplBuilder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }
        
        public TransactionRequestImplBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public TransactionRequestImplBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }
        
        public TransactionRequestImplBuilder transactionType(String transactionType) {
            this.transactionType = transactionType;
            return this;
        }
        
        public TransactionRequestImplBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public TransactionRequestImplBuilder location(String location) {
            this.location = location;
            return this;
        }
        
        public TransactionRequestImplBuilder deviceInfo(String deviceInfo) {
            this.deviceInfo = deviceInfo;
            return this;
        }
        
        public TransactionRequestImplBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }
        
        public TransactionRequestImplBuilder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }
        
        public TransactionRequestImplBuilder merchantId(String merchantId) {
            this.merchantId = merchantId;
            return this;
        }
        
        public TransactionRequestImplBuilder productCategory(String productCategory) {
            this.productCategory = productCategory;
            return this;
        }
        
        public TransactionRequestImpl build() {
            TransactionRequestImpl request = new TransactionRequestImpl();
            request.transactionId = this.transactionId;
            request.userId = this.userId;
            request.amount = this.amount;
            request.transactionType = this.transactionType;
            request.timestamp = this.timestamp;
            request.location = this.location;
            request.deviceInfo = this.deviceInfo;
            request.ipAddress = this.ipAddress;
            request.paymentMethod = this.paymentMethod;
            request.merchantId = this.merchantId;
            request.productCategory = this.productCategory;
            return request;
        }
    }
    
    /**
     * 获取交易IP地址
     * 
     * @return 用户IP地址
     */
    public String getIpAddress() {
        return this.ipAddress;
    }
    
    /**
     * 获取设备信息
     * 
     * @return 用户设备标识
     */
    public String getDeviceInfo() {
        return this.deviceInfo;
    }
    
    /**
     * 获取交易位置
     * 
     * @return 交易地理位置
     */
    public String getLocation() {
        return this.location;
    }
    
    /**
     * 获取交易时间戳
     * 
     * @return 交易发生时间
     */
    public String getTimestamp() {
        return this.timestamp;
    }
    
    /**
     * 获取交易类型
     * 
     * @return 交易类型标识
     */
    public String getTransactionType() {
        return this.transactionType;
    }
    
    /**
     * 获取交易金额
     * <p>
     * 使用BigDecimal确保金额精度
     * 
     * @return 交易金额
     */
    public BigDecimal getAmount() {
        return this.amount;
    }
    
    /**
     * 获取用户ID
     * 
     * @return 用户唯一标识
     */
    public String getUserId() {
        return this.userId;
    }
    
    /**
     * 获取交易ID
     * 
     * @return 交易唯一标识
     */
    public String getTransactionId() {
        return this.transactionId;
    }
}