package com.bone.system.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.system.infrastructure.config.GlobalExceptionHandler;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 「业务码 → HTTP 状态 → 响应体 errorCode」映射回归（纯单测，无 Spring 上下文）。
 *
 * <p><b>为何需要本测试</b>：{@link SystemErrors#of} 若退回三参 {@code new BizException(status, message, cause)}，
 * {@code errorCode} 会被置 {@code null} —— 响应依然 404、message 依然是 {@code SYS_CONFIG_NOT_FOUND: 42}，
 * 任何按「状态 + message」断言的测试都照旧绿，只有前端 {@code i18n.t('errors.' + errorCode)} 的分支静默失效 （英文用户只能看到中文
 * fallback）。这里把它钉成硬断言。
 */
class SystemErrorsMappingTest {

  /**
   * 端到端：{@code SystemErrors.of(...)} → advice → {@code ProblemDetail.errorCode}。
   *
   * <p>锁定两件事：{@code errorCode} 非 {@code null}（防三参回退），且 HTTP 状态取自「码 → 状态」表。
   */
  private static ResponseEntity<ApiResponse<ProblemDetail>> handle(String errorCode) {
    ResponseEntity<ApiResponse<ProblemDetail>> resp =
        new GlobalExceptionHandler().handleBizException(SystemErrors.of(errorCode));
    assertEquals(
        errorCode,
        resp.getBody().getData().getErrorCode(),
        "errorCode 必须原样透传到 ProblemDetail，否则前端 i18n 断链");
    return resp;
  }

  @Test
  void of_carriesErrorCode_notNull() {
    BizException ex = SystemErrors.of(SystemErrorCodes.CONFIG_NOT_FOUND);
    assertEquals(SystemErrorCodes.CONFIG_NOT_FOUND, ex.getErrorCode());
    assertEquals("SYS_CONFIG_NOT_FOUND", ex.getErrorCode());
  }

  @Test
  void ofWithDetail_carriesErrorCode_andKeepsMessageFallback() {
    BizException ex = SystemErrors.of(SystemErrorCodes.CONFIG_NOT_FOUND, 42L);
    assertEquals(SystemErrorCodes.CONFIG_NOT_FOUND, ex.getErrorCode());
    assertEquals("SYS_CONFIG_NOT_FOUND: 42", ex.getMessage());
  }

  @Test
  void supplier_carriesErrorCode() {
    Supplier<BizException> supplier = SystemErrors.supplier(SystemErrorCodes.LOG_NOT_FOUND, "id-1");
    assertEquals(SystemErrorCodes.LOG_NOT_FOUND, supplier.get().getErrorCode());
  }

  @Test
  void notFound_mapsTo404() {
    assertEquals(HttpStatus.NOT_FOUND, handle(SystemErrorCodes.CONFIG_NOT_FOUND).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, handle(SystemErrorCodes.LOG_NOT_FOUND).getStatusCode());
  }

  @Test
  void conflict_mapsTo409() {
    assertEquals(HttpStatus.CONFLICT, handle(SystemErrorCodes.CONFIG_KEY_CONFLICT).getStatusCode());
    assertEquals(HttpStatus.CONFLICT, handle(SystemErrorCodes.DICT_CYCLE_DETECTED).getStatusCode());
  }

  @Test
  void readonly_mapsTo403() {
    assertEquals(HttpStatus.FORBIDDEN, handle(SystemErrorCodes.DICT_TYPE_READONLY).getStatusCode());
  }

  @Test
  void invalid_mapsTo400() {
    assertEquals(
        HttpStatus.BAD_REQUEST, handle(SystemErrorCodes.LOG_LEVEL_INVALID).getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST, handle(SystemErrorCodes.DICT_VALUE_INVALID).getStatusCode());
  }

  @Test
  void runFailed_mapsTo500() {
    assertEquals(
        HttpStatus.INTERNAL_SERVER_ERROR,
        handle(SystemErrorCodes.SCHEDULE_TASK_RUN_FAILED).getStatusCode());
  }

  /** 未登记的码必须 fail-fast，而不是被兜底成 400 溜到运行期。 */
  @Test
  void unregisteredCode_failsFast() {
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> SystemErrors.of("SYS_NO_SUCH_CODE"));
    assertTrue(
        ex.getMessage().contains("SYS_NO_SUCH_CODE"), "异常信息应点名未登记的码，便于定位: " + ex.getMessage());
  }
}
