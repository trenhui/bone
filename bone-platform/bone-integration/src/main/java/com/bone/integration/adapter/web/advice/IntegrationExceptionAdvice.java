package com.bone.integration.adapter.web.advice;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 集成模块异常映射：未实现连接器返回 HTTP 501，避免假成功（INT-01）。
 */
@RestControllerAdvice
public class IntegrationExceptionAdvice {

    private static final int NOT_IMPLEMENTED_CODE = 501;

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> notImplemented(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(NOT_IMPLEMENTED_CODE, "INT_CONNECTOR_NOT_IMPLEMENTED: " + ex.getMessage()));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> bizException(BizException ex) {
        HttpStatus status =
                ex.getCode() == NOT_IMPLEMENTED_CODE ? HttpStatus.NOT_IMPLEMENTED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }
}
