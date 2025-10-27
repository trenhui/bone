package com.bone.procurement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地错误响应类，避免依赖问题
 */
class ErrorResponse {
    private int status;
    private String errorCode;
    private String message;
    private String path;
    
    public ErrorResponse(int status, String errorCode, String message, String path) {
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
}

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
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        // 使用异常中设置的HTTP状态码
        HttpStatus status = ex.getHttpStatus();
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode() : "BUSINESS_ERROR";
        
        if (ex.isLogDetail()) {
            log.warn("业务异常: {}, 错误码: {}", ex.getMessage(), errorCode);
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                errorCode,
                ex.getMessage(),
                request.getDescription(false)
        );
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    /**
     * 处理参数验证异常
     * @param ex 参数验证异常
     * @param request Web请求
     * @return 响应实体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex,
                                                                 WebRequest request) {
        log.warn("参数验证失败");
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "参数验证失败",
                request.getDescription(false)
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理空指针异常
     * @param ex 空指针异常
     * @param request Web请求
     * @return 错误响应
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ErrorResponse handleNullPointerException(NullPointerException ex, WebRequest request) {
        log.error("空指针异常", ex);
        
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "NULL_POINTER",
                "处理请求时发生内部错误",
                request.getDescription(false)
        );
    }
    
    /**
     * 处理所有其他未捕获的异常
     * @param ex 异常
     * @param request Web请求
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ErrorResponse handleAllExceptions(Exception ex, WebRequest request) {
        log.error("未处理的异常", ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "服务器内部错误",
                request.getDescription(false)
        );
        
        return errorResponse;
    }
}
