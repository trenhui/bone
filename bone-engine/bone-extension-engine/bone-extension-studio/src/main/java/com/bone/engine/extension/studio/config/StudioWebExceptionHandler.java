package com.bone.engine.extension.studio.config;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Studio API 统一异常 → {@link ApiResponse} + {@link ProblemDetail}。 */
@RestControllerAdvice(basePackages = "com.bone.engine.extension.studio.controller")
public class StudioWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StudioWebExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ProblemDetail>> badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<ProblemDetail>> conflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "EXT_STATE_INVALID", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ProblemDetail>> internal(Exception ex) {
        log.error("[API] unhandled traceId={}", MDC.get(StudioTraceIdFilter.TRACE_ID), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "服务内部错误");
    }

    private static ResponseEntity<ApiResponse<ProblemDetail>> problem(
            HttpStatus status, String errorCode, String detail) {
        ProblemDetail body = ProblemDetail.of(errorCode, status.value(), detail);
        body.setTraceId(MDC.get(StudioTraceIdFilter.TRACE_ID));
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), detail, body));
    }
}
