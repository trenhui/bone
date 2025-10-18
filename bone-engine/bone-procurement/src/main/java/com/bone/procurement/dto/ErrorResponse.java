package com.bone.procurement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 错误响应DTO
 * 提供标准化的错误响应格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * HTTP状态码
     */
    private int status;
    
    /**
     * 错误码
     */
    private String errorCode;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 请求路径
     */
    private String path;
    
    /**
     * 错误时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 构造函数
     * @param status HTTP状态码
     * @param errorCode 错误码
     * @param message 错误消息
     * @param path 请求路径
     */
    public ErrorResponse(int status, String errorCode, String message, String path) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
}