package com.bone.demo.infrastructure.config;

import lombok.Data;

/**
 * API响应结果
 */
@Data
public class ApiResponse<T> {
    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    private ApiResponse(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }
    
    /**
     * 成功响应带消息
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }
    
    /**
     * 失败响应
     */
    public static <T> ApiResponse<T> fail(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }
    
    /**
     * 参数错误响应
     */
    public static <T> ApiResponse<T> paramError(String message) {
        return new ApiResponse<>(400, message, null);
    }
    
    /**
     * 服务器错误响应
     */
    public static <T> ApiResponse<T> serverError() {
        return new ApiResponse<>(500, "服务器内部错误", null);
    }
}