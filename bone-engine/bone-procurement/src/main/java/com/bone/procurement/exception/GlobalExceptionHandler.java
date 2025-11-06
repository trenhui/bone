package com.bone.procurement.exception;

import com.bone.procurement.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局异常处理器
 * 统一处理系统中的各种异常，并返回标准化的错误响应
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     * @param ex 业务异常
     * @param request Web请求
     * @return 响应实体
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, WebRequest request) {
        // 使用异常中设置的HTTP状态码
        HttpStatus status = ex.getHttpStatus();
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR";
        
        if (ex.isLogDetail()) {
            log.warn("业务异常: {}, 错误码: {}", ex.getMessage(), errorCode);
        }
        
        ApiResponse<Void> response = ApiResponse.failure(
                status.value(),
                ex.getMessage()
        );
        
        return new ResponseEntity<>(response, status);
    }

    /**
     * 处理参数验证异常
     * @param ex 参数验证异常
     * @param request Web请求
     * @return 响应实体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                           WebRequest request) {
        log.warn("参数验证失败");
        
        BindingResult bindingResult = ex.getBindingResult();
        List<ApiResponse.ErrorDetail> errors = new ArrayList<>();
        
        // 提取所有字段验证错误
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            ApiResponse.ErrorDetail errorDetail = new ApiResponse.ErrorDetail(
                    fieldError.getField(),
                    fieldError.getCode(),
                    fieldError.getDefaultMessage()
            );
            errors.add(errorDetail);
        }
        
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.BAD_REQUEST.value(),
                "参数验证失败",
                errors
        );
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理空指针异常
     * @param ex 空指针异常
     * @param request Web请求
     * @return 错误响应
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleNullPointerException(NullPointerException ex, WebRequest request) {
        log.error("空指针异常", ex);
        
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "处理请求时发生内部错误"
        );
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理实体未找到异常
     * @param ex 实体未找到异常
     * @param request Web请求
     * @return 错误响应
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        log.warn("实体未找到: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage()
        );
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * 处理非法参数异常
     * @param ex 非法参数异常
     * @param request Web请求
     * @return 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("非法参数: {}", ex.getMessage());
        
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.BAD_REQUEST.value(),
                "参数非法: " + ex.getMessage()
        );
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex, WebRequest request) {
        log.error("未处理的异常", ex);
        
        ApiResponse<Void> response = ApiResponse.failure(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "服务器内部错误"
        );
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
