package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.bone.tool.codegen.domain.exception.CodegenBusinessException;

import java.sql.SQLException;

/**
 * 全局异常处理器
 * <p>
 * 捕获并处理应用程序中抛出的各种异常，确保返回统一格式的错误响应
 * 使用@ControllerAdvice和@ExceptionHandler注解实现全局异常处理
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理参数验证异常
     * 
     * @param ex 方法参数验证异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ApiResponse<String> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldError() != null 
                ? ex.getBindingResult().getFieldError().getDefaultMessage() 
                : "请求参数验证失败";
        logger.warn("参数验证失败: {}", errorMessage);
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMessage);
    }

    /**
     * 处理绑定异常（表单参数）
     * 
     * @param ex 绑定异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ApiResponse<String> handleBindException(BindException ex) {
        String errorMessage = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "请求参数绑定失败";
        logger.warn("参数绑定失败: {}", errorMessage);
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMessage);
    }

    /**
     * 处理HTTP消息不可读异常（JSON解析错误）
     * 
     * @param ex HTTP消息不可读异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ApiResponse<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        logger.warn("请求体格式错误: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), "请求体格式错误，请检查JSON格式是否正确");
    }

    /**
     * 处理SQL异常
     * 
     * @param ex SQL异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<String> handleSQLException(SQLException ex) {
        logger.error("数据库操作异常: {}", ex.getMessage(), ex);
        return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "数据库操作失败，请稍后重试");
    }

    /**
     * 处理HTTP请求方法不支持异常
     * 
     * @param ex HTTP请求方法不支持异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ResponseBody
    public ApiResponse<String> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        logger.warn("不支持的请求方法: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持的请求方法");
    }

    /**
     * 处理路径不存在异常
     * 
     * @param ex 路径不存在异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public ApiResponse<String> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        logger.warn("请求路径不存在");
        return ApiResponse.error(HttpStatus.NOT_FOUND.value(), "请求路径不存在");
    }

    /**
     * 处理参数异常（IllegalArgumentException）
     * 
     * @param ex 参数异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ApiResponse<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("参数错误: {}", ex.getMessage());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    /**
     * 处理运行时异常
     * 
     * @param ex 运行时异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(CodegenBusinessException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> handleCodegenBusinessException(CodegenBusinessException ex) {
        logger.warn("业务异常: {}", ex.getMessage());
        int code = ex.getErrorCode();
        int statusCode = (code >= 400 && code < 600) ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus status = HttpStatus.valueOf(statusCode);
        return new ResponseEntity<>(
                ApiResponse.error(code, ex.getMessage()),
                status);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<String> handleRuntimeException(RuntimeException ex) {
        logger.error("运行时异常: {}", ex.getMessage(), ex);
        return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "系统内部错误，请稍后重试");
    }

    /**
     * 处理所有其他异常
     * 
     * @param ex 异常
     * @return 包含错误信息的API响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<String> handleException(Exception ex) {
        logger.error("未知异常: {}", ex.getMessage(), ex);
        return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "系统内部错误，请稍后重试");
    }

    /**
     * 处理特定业务异常（可以扩展为自定义业务异常类）
     * 
     * @param ex 异常
     * @return 包含错误信息的API响应
     */
}