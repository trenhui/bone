package com.bone.procurement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 统一API响应包装类
 * 提供标准化的API响应格式，包含数据、状态、消息和元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 响应状态码
     */
    private int code;

    /**
     * 响应状态
     */
    private boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 错误详情（仅在失败时返回）
     */
    private List<ErrorDetail> errors;

    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 分页元数据（仅在查询列表时返回）
     */
    private PaginationMetadata pagination;

    /**
     * 错误详情类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String field;
        private String code;
        private String message;
    }

    /**
     * 分页元数据类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationMetadata {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /**
     * 创建成功响应
     * @param data 响应数据
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .success(true)
                .message("操作成功")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建成功响应（带自定义消息）
     * @param data 响应数据
     * @param message 自定义消息
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建分页查询成功响应
     * @param data 响应数据
     * @param page 页码
     * @param size 每页大小
     * @param totalElements 总元素数
     * @param totalPages 总页数
     * @return 分页查询成功响应对象
     */
    public static <T> ApiResponse<T> successWithPagination(T data, int page, int size, long totalElements, int totalPages) {
        PaginationMetadata pagination = PaginationMetadata.builder()
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
        
        return ApiResponse.<T>builder()
                .code(200)
                .success(true)
                .message("查询成功")
                .data(data)
                .pagination(pagination)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败响应
     * @param code 错误码
     * @param message 错误消息
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> failure(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败响应（带错误详情）
     * @param code 错误码
     * @param message 错误消息
     * @param errors 错误详情列表
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> failure(int code, String message, List<ErrorDetail> errors) {
        return ApiResponse.<T>builder()
                .code(code)
                .success(false)
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建参数验证失败响应
     * @param field 字段名
     * @param code 错误码
     * @param message 错误消息
     * @return 参数验证失败响应对象
     */
    public static <T> ApiResponse<T> validationError(String field, String code, String message) {
        ErrorDetail errorDetail = new ErrorDetail(field, code, message);
        return ApiResponse.<T>builder()
                .code(400)
                .success(false)
                .message("参数验证失败")
                .errors(Collections.singletonList(errorDetail))
                .timestamp(LocalDateTime.now())
                .build();
    }
}