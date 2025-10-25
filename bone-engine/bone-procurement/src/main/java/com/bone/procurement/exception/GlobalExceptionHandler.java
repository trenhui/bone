package com.bone.procurement.exception;

import com.bone.procurement.dto.ErrorResponse;
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
        // 使用默认的BAD_REQUEST状态码
        HttpStatus status = HttpStatus.BAD_REQUEST;
        
        log.warn("业务异常: {}", ex.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                "BUSINESS_ERROR",
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
     * @return 响应实体
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
     * @return 响应实体
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ErrorResponse handleAllExceptions(Exception ex, WebRequest request) {
        log.error("未预期的异常", ex);
        
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "系统内部错误，请联系管理员",
                request.getDescription(false)
        );
    }
}