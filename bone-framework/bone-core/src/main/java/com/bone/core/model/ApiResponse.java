package com.bone.core.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 统一API响应模型
 * 简洁易用，业界最佳实践命名
 *
 * @author Bone Framework Team
 * @since 2025
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Boolean success;
    private Integer code;
    private String message;
    private T data;
    private String timestamp;

    // 私有构造方法
    private ApiResponse(Boolean success, Integer code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toString();
    }

    // === 成功响应方法 ===

    /**
     * 成功响应（最常用）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应（仅消息）
     */
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(), message, null);
    }

    // === 错误响应方法 ===

    /**
     * 错误响应（最常用）
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, ResultCode.SERVER_ERROR.getCode(), message, null);
    }

    /**
     * 错误响应（使用状态码枚举）
     */
    public static <T> ApiResponse<T> error(ResultCode resultCode) {
        return new ApiResponse<>(false, resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 错误响应（自定义状态码和消息）
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    /**
     * 错误响应（HTTP/业务码 + 消息 + ProblemDetail 等 data）
     */
    public static <T> ApiResponse<T> error(Integer code, String message, T errorData) {
        return new ApiResponse<>(false, code, message, errorData);
    }

    /**
     * 错误响应（带错误详情数据）
     */
    public static <T> ApiResponse<T> error(String message, T errorData) {
        return new ApiResponse<>(false, ResultCode.SERVER_ERROR.getCode(), message, errorData);
    }

    /**
     * 错误响应（完整参数）
     */
    public static <T> ApiResponse<T> error(ResultCode resultCode, String message, T errorData) {
        return new ApiResponse<>(false, resultCode.getCode(), message, errorData);
    }

    // === 分页响应方法 ===

    /**
     * 分页响应
     */
    public static <T> ApiResponse<PageResult<T>> page(PageResult<T> pageResult) {
        return success(pageResult);
    }

    /**
     * 快速分页响应
     */
    public static <T> ApiResponse<PageResult<T>> page(List<T> records, Long total,
                                                      Integer page, Integer size) {
        PageResult<T> pageResult = PageResult.of(records, total, page, size);
        return success(pageResult);
    }

    // === 便捷判断方法 ===

    /**
     * 判断是否成功
     */
    public Boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 判断是否失败
     */
    public Boolean isError() {
        return !isSuccess();
    }
}