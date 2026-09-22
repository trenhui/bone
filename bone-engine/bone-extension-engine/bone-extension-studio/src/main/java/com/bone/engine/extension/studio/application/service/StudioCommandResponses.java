package com.bone.engine.extension.studio.application.service;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.config.StudioRequestContextFilter;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

/** REST 响应组装（供 CommandHandler 使用，与 adapter {@code StudioHttpSupport} 契约一致）。 */
public final class StudioCommandResponses {

  private StudioCommandResponses() {}

  public static String resourceLocation(String collection, Long id) {
    return "/api/v1/extension/" + collection + "/" + id;
  }

  public static String operationLocation(String operationId) {
    return "/api/v1/extension/operations/" + operationId;
  }

  public static <T> ResponseEntity<ApiResponse<T>> created(
      String location, String message, T data) {
    ApiResponse<T> body = ApiResponse.success(message, data);
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.LOCATION, location)
        .eTag(etag(data))
        .body(body);
  }

  public static <T> ResponseEntity<ApiResponse<T>> accepted(
      String location, String message, T data) {
    ApiResponse<T> body = ApiResponse.success(message, data);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header(HttpHeaders.LOCATION, location)
        .body(body);
  }

  public static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
    ApiResponse<T> body = ApiResponse.success(message, data);
    return ResponseEntity.ok().eTag(etag(data)).body(body);
  }

  @SuppressWarnings("unchecked")
  public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
    return (ResponseEntity<ApiResponse<T>>)
        (Object) problem(HttpStatus.BAD_REQUEST, StudioErrorCodes.VALIDATION_FAILED, message);
  }

  @SuppressWarnings("unchecked")
  public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
    return (ResponseEntity<ApiResponse<T>>)
        (Object) problem(HttpStatus.NOT_FOUND, StudioErrorCodes.RESOURCE_NOT_FOUND, message);
  }

  public static ResponseEntity<ApiResponse<ProblemDetail>> problem(
      HttpStatus status, String errorCode, String detail) {
    ProblemDetail body = ProblemDetail.of(errorCode, status.value(), detail);
    body.setTraceId(MDC.get(StudioRequestContextFilter.TRACE_ID));
    return ResponseEntity.status(status).body(ApiResponse.error(status.value(), detail, body));
  }

  public static String etag(Object entity) {
    if (entity == null) {
      return null;
    }
    Integer version = null;
    if (entity instanceof ExtPoint point) {
      version = point.getVersion();
    } else if (entity instanceof Extension extension) {
      version = extension.getVersion();
    }
    return version != null ? "\"v" + version + "\"" : null;
  }

  public static Optional<Integer> parseIfMatchVersion(String ifMatch) {
    if (!StringUtils.hasText(ifMatch)) {
      return Optional.empty();
    }
    String token = ifMatch.trim();
    if (token.startsWith("W/")) {
      token = token.substring(2).trim();
    }
    if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
      token = token.substring(1, token.length() - 1);
    }
    if (token.startsWith("v") || token.startsWith("V")) {
      token = token.substring(1);
    }
    try {
      return Optional.of(Integer.parseInt(token));
    } catch (NumberFormatException ex) {
      return Optional.empty();
    }
  }
}
