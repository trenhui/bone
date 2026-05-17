package com.bone.engine.extension.studio.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.config.StudioRequestContextFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 统一错误响应（ApiResponse + ProblemDetail）。 */
public final class StudioApiResponses {

    private StudioApiResponses() {}

    @SuppressWarnings("unchecked")
    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return (ResponseEntity<ApiResponse<T>>) (Object) problem(
                HttpStatus.BAD_REQUEST, StudioErrorCodes.VALIDATION_FAILED, message);
    }

    @SuppressWarnings("unchecked")
    public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return (ResponseEntity<ApiResponse<T>>) (Object) problem(
                HttpStatus.NOT_FOUND, StudioErrorCodes.RESOURCE_NOT_FOUND, message);
    }

    public static ResponseEntity<ApiResponse<ProblemDetail>> problem(
            HttpStatus status, String errorCode, String detail) {
        ProblemDetail body = ProblemDetail.of(errorCode, status.value(), detail);
        body.setTraceId(MDC.get(StudioRequestContextFilter.TRACE_ID));
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), detail, body));
    }
}
