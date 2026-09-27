package com.bone.metadata.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * F1 回归：{@link MetadataErrorCodes} 常量类 + {@link GlobalExceptionHandler#handleRuntimeRecord} 的「业务码 →
 * HTTP 状态 → 响应体 errorCode」映射。纯单测（无 Spring / Redis 上下文）， 锁定错误码契约：码字符串一旦拼错或 GlobalExceptionHandler
 * 漏登记分支，测试即红。
 */
class MetadataErrorCodesMappingTest {

  private static final String MSG = "boom";

  private static ResponseEntity<ApiResponse<ProblemDetail>> call(String code) {
    ResponseEntity<ApiResponse<ProblemDetail>> resp =
        new GlobalExceptionHandler().handleRuntimeRecord(new RuntimeRecordException(code, MSG));
    assertEquals(code, resp.getBody().getData().getErrorCode(), "errorCode 应原样透传");
    return resp;
  }

  @Test
  void runtimeRecordNotFound_mapsTo404() {
    assertEquals(
        HttpStatus.NOT_FOUND, call(MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND).getStatusCode());
  }

  @Test
  void runtimeEntityNotFound_mapsTo404() {
    assertEquals(
        HttpStatus.NOT_FOUND, call(MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND).getStatusCode());
  }

  @Test
  void preconditionFailed_mapsTo412() {
    assertEquals(
        HttpStatus.PRECONDITION_FAILED,
        call(MetadataErrorCodes.PRECONDITION_FAILED).getStatusCode());
  }

  @Test
  void runtimeValidationFailed_mapsTo400() {
    assertEquals(
        HttpStatus.BAD_REQUEST, call(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED).getStatusCode());
  }

  @Test
  void runtimeDuplicate_mapsTo409() {
    assertEquals(HttpStatus.CONFLICT, call(MetadataErrorCodes.RUNTIME_DUPLICATE).getStatusCode());
  }

  @Test
  void runtimeInvalidQuery_mapsTo400() {
    assertEquals(
        HttpStatus.BAD_REQUEST, call(MetadataErrorCodes.RUNTIME_INVALID_QUERY).getStatusCode());
  }

  @Test
  void runtimeInvalidIdentifier_mapsTo400() {
    assertEquals(
        HttpStatus.BAD_REQUEST,
        call(MetadataErrorCodes.RUNTIME_INVALID_IDENTIFIER).getStatusCode());
  }

  /** 常量值与错误码登记 §6 / 前端 errors.* 键必须完全一致，否则 i18n 断链。 */
  @Test
  void constantsMatchRegisteredCodes() {
    assertEquals("META_RUNTIME_RECORD_NOT_FOUND", MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND);
    assertEquals("META_RUNTIME_ENTITY_NOT_FOUND", MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND);
    assertEquals("META_PRECONDITION_FAILED", MetadataErrorCodes.PRECONDITION_FAILED);
    assertEquals("META_RUNTIME_VALIDATION_FAILED", MetadataErrorCodes.RUNTIME_VALIDATION_FAILED);
    assertEquals("META_RUNTIME_DUPLICATE", MetadataErrorCodes.RUNTIME_DUPLICATE);
    assertEquals("META_RUNTIME_INVALID_QUERY", MetadataErrorCodes.RUNTIME_INVALID_QUERY);
    assertEquals("META_RUNTIME_INVALID_IDENTIFIER", MetadataErrorCodes.RUNTIME_INVALID_IDENTIFIER);
  }
}
