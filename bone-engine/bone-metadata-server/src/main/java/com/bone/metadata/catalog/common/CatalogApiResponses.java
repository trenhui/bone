package com.bone.metadata.catalog.common;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** catalog / metadata REST 统一响应与 ProblemDetail 组装。 */
public final class CatalogApiResponses {

  private CatalogApiResponses() {}

  public static ResponseEntity<ApiResponse<ProblemDetail>> problem(
      HttpStatus status, String errorCode, String detail) {
    ProblemDetail body = ProblemDetail.of(errorCode, status.value(), detail);
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      body.setTraceId(traceId);
    }
    return ResponseEntity.status(status).body(ApiResponse.fail(status.value(), detail, body));
  }
}
