package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.controller.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Studio API 统一异常 → ApiResponse（后续可映射 ProblemDetail）。 */
@RestControllerAdvice(basePackages = "com.bone.engine.extension.studio.controller")
public class StudioWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StudioWebExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> badRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> conflict(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "EXT_STATE_INVALID", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> internal(Exception ex) {
        log.error("[API] unhandled traceId={}", MDC.get("traceId"), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "服务内部错误");
    }

    private static ResponseEntity<ApiResponse<Map<String, Object>>> error(
            HttpStatus status, String errorCode, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("errorCode", errorCode);
        detail.put("traceId", MDC.get("traceId"));
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message, detail));
    }
}
