package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.function.Supplier;

/**
 * 控制器异常处理工具类
 * <p>
 * 提供统一的异常处理模式，减少控制器中的代码重复
 * 封装日志记录、异常捕获和响应构建的通用逻辑
 */
public class ControllerExceptionHandler {

    /**
     * 处理带有返回值的控制器方法的异常
     * 
     * @param logger 日志记录器
     * @param operation 操作描述
     * @param action 实际执行的操作
     * @param <T> 返回值类型
     * @return 包含操作结果的API响应
     */
    public static <T> ApiResponse<T> handleException(Logger logger, String operation, Supplier<T> action) {
        logger.info("开始{}", operation);
        try {
            T result = action.get();
            logger.info("{}成功", operation);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            logger.warn("{}参数错误: {}", operation, e.getMessage());
            return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        } catch (Exception e) {
            logger.error("{}失败: {}", operation, e.getMessage(), e);
            return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), operation + "失败: " + e.getMessage());
        }
    }

    /**
     * 处理不带返回值的控制器方法的异常
     * 
     * @param logger 日志记录器
     * @param operation 操作描述
     * @param action 实际执行的操作
     * @return 成功/失败的API响应
     */
    public static ApiResponse<Boolean> handleVoidException(Logger logger, String operation, Runnable action) {
        logger.info("开始{}", operation);
        try {
            action.run();
            logger.info("{}成功", operation);
            return ApiResponse.success(true);
        } catch (IllegalArgumentException e) {
            logger.warn("{}参数错误: {}", operation, e.getMessage());
            return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        } catch (Exception e) {
            logger.error("{}失败: {}", operation, e.getMessage(), e);
            return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), operation + "失败: " + e.getMessage());
        }
    }

    /**
     * 处理带有ResponseEntity的控制器方法的异常
     * 
     * @param logger 日志记录器
     * @param operation 操作描述
     * @param action 实际执行的操作
     * @param <T> 响应体类型
     * @return 包含操作结果的ResponseEntity
     */
    public static <T> ResponseEntity<ApiResponse<T>> handleResponseEntity(Logger logger, String operation, Supplier<ResponseEntity<ApiResponse<T>>> action) {
        logger.info("开始{}", operation);
        try {
            ResponseEntity<ApiResponse<T>> result = action.get();
            logger.info("{}成功", operation);
            return result;
        } catch (Exception e) {
            logger.error("{}失败: {}", operation, e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), operation + "失败: " + e.getMessage()));
        }
    }
}