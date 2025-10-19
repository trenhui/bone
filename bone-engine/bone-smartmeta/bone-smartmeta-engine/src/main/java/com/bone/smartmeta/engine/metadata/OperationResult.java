package com.bone.smartmeta.engine.metadata;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

/**
 * 操作执行结果
 * <p>提供标准化的操作结果封装，支持成功/失败状态、消息、数据、错误码和详细信息</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean success;
    private String message;
    private Object data;
    private String errorCode;
    private Map<String, Object> details = new HashMap<>();
    
    /**
     * 创建成功结果
     */
    public static OperationResult success() {
        return OperationResult.builder()
                .success(true)
                .message("操作执行成功")
                .build();
    }
    
    /**
     * 创建带数据的成功结果
     */
    public static OperationResult success(Object data, String message) {
        return OperationResult.builder()
                .success(true)
                .data(data)
                .message(message != null ? message : "操作执行成功")
                .build();
    }
    
    /**
     * 创建只有数据的成功结果
     */
    public static OperationResult success(Object data) {
        return success(data, "操作执行成功");
    }
    
    /**
     * 创建只有消息的成功结果
     */
    public static OperationResult successWithMessage(String message) {
        return success(null, message);
    }
    
    /**
     * 创建失败结果
     */
    public static OperationResult failure(String message) {
        return OperationResult.builder()
                .success(false)
                .message(message)
                .build();
    }
    
    /**
     * 创建带错误码的失败结果
     */
    public static OperationResult failure(String message, String errorCode) {
        return OperationResult.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }
    
    /**
     * 添加详细信息
     */
    public OperationResult addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }
    
    /**
     * 添加多个详细信息
     */
    public OperationResult addDetails(Map<String, Object> details) {
        if (details != null) {
            this.details.putAll(details);
        }
        return this;
    }
    
    /**
     * 获取详细信息（返回不可修改的Map）
     */
    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(this.details);
    }
    
    /**
     * 设置详细信息
     */
    public void setDetails(Map<String, Object> details) {
        this.details = new HashMap<>(details != null ? details : Collections.emptyMap());
    }
    
    /**
     * 克隆当前结果
     */
    public OperationResult clone() {
        return OperationResult.builder()
                .success(this.success)
                .message(this.message)
                .data(this.data)
                .errorCode(this.errorCode)
                .details(new HashMap<>(this.details))
                .build();
    }
}