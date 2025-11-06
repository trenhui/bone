/**
 * 订单处理结果类
 * 封装订单状态转换和业务处理的结果信息
 */
package com.bone.procurement.state;

import java.util.HashMap;
import java.util.Map;

public class OrderProcessingResult {
    
    private boolean success;
    private String message;
    private String errorMessage;
    private Map<String, Object> additionalInfo = new HashMap<>();
    
    private OrderProcessingResult(boolean success) {
        this.success = success;
    }
    
    /**
     * 创建成功的处理结果
     * @return 成功结果对象
     */
    public static OrderProcessingResult success() {
        OrderProcessingResult result = new OrderProcessingResult(true);
        result.message = "操作成功";
        return result;
    }
    
    /**
     * 创建成功的处理结果
     * @param message 成功消息
     * @return 成功结果对象
     */
    public static OrderProcessingResult success(String message) {
        OrderProcessingResult result = new OrderProcessingResult(true);
        result.message = message;
        return result;
    }
    
    /**
     * 创建失败的处理结果
     * @param errorMessage 错误消息
     * @return 失败结果对象
     */
    public static OrderProcessingResult failure(String errorMessage) {
        OrderProcessingResult result = new OrderProcessingResult(false);
        result.errorMessage = errorMessage;
        return result;
    }
    
    /**
     * 添加额外信息
     * @param key 键
     * @param value 值
     * @return 当前结果对象
     */
    public OrderProcessingResult withAdditionalInfo(String key, Object value) {
        this.additionalInfo.put(key, value);
        return this;
    }
    
    /**
     * 设置成功消息
     * @param message 成功消息
     * @return 当前结果对象
     */
    public OrderProcessingResult withMessage(String message) {
        this.message = message;
        return this;
    }
    
    // Getters
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public Map<String, Object> getAdditionalInfo() {
        return additionalInfo;
    }
    
    @Override
    public String toString() {
        return "OrderProcessingResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", additionalInfo=" + additionalInfo +
                '}';
    }
}