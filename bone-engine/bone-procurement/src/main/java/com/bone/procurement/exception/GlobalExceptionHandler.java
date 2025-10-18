package com.bone.procurement.exception;

import com.bone.procurement.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理系统中的各种异常，并返回标准化的错误响应
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 处理业务异常
     * @param ex 业务异常
     * @param request Web请求
     * @return 响应实体
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        HttpStatus status = ex.getHttpStatus();
        
        // 根据是否需要记录详细日志决定日志级别
        if (ex.isLogDetail()) {
            log.error("业务异常: {}", ex.getMessage(), ex);
        } else {
            log.warn("业务异常: {}", ex.getMessage());
        }
        
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                request.getDescription(false)
        );
        
        return new ResponseEntity<>(errorResponse, status);
    }
    
    /**
     * 处理参数验证异常
     * @param ex 参数验证异常
     * @param headers HTTP头
     * @param status HTTP状态码
     * @param request Web请求
     * @return 响应实体
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpHeaders headers,
                                                                 HttpStatus status,
                                                                 WebRequest request) {
        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        
        // 收集字段验证错误信息
        String errorMessage = fieldErrors.stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("；"));
        
        log.warn("参数验证失败: {}", errorMessage);
        
        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                "INVALID_PARAMETER",
                errorMessage,
                request.getDescription(false)
        );
        
        return new ResponseEntity<>(errorResponse, headers, status);
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