package com.bone.engine.extension.studio.controller.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一的API响应包装器
 * 用于所有Controller返回标准化的响应格式
 */
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Map<String, Object> meta;
    
    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.meta = new HashMap<>();
    }
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "操作成功", data);
    }
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
    
    public ApiResponse<T> withMeta(String key, Object value) {
        this.meta.put(key, value);
        return this;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public T getData() {
        return data;
    }
    
    public Map<String, Object> getMeta() {
        return meta;
    }
}