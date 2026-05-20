package com.bone.masterdata.infrastructure.config;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.exception.SystemException;
import com.bone.core.result.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int NOT_IMPLEMENTED_CODE = 501;

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BizException e) {
        return ResponseEntity.status(resolveHttpStatus(e.getCode()))
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    private static HttpStatus resolveHttpStatus(int code) {
        if (code == NOT_IMPLEMENTED_CODE) {
            return HttpStatus.NOT_IMPLEMENTED;
        }
        HttpStatus resolved = HttpStatus.resolve(code);
        if (resolved != null && (resolved.is4xxClientError() || resolved.is5xxServerError())) {
            return resolved;
        }
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, e.getMessage()));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(SystemException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(500, "系统内部错误"));
    }
}
