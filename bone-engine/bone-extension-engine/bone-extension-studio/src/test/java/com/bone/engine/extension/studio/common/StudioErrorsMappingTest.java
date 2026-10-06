package com.bone.engine.extension.studio.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.config.StudioWebExceptionHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** 锁死「错误码 → HTTP 状态 → 响应体 errorCode」三段契约（X-2）。 */
class StudioErrorsMappingTest {

  @Test
  @DisplayName("每个码常量都有登记状态，且状态落在 4xx/5xx")
  void everyCodeHasRegisteredStatus() throws IllegalAccessException {
    int checked = 0;
    for (Field field : StudioErrorCodes.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
        continue;
      }
      String code = (String) field.get(null);
      int status = StudioErrors.httpStatusOf(code);
      assertTrue(status >= 400 && status <= 599, code + " 状态越界: " + status);
      checked++;
    }
    // 2026-10-05：5 个 COMMON_* 码的模块副本已删除（收敛到 bone-core 的 CommonErrorCodes），
    // 本类声明的常量数 13 → 8。下面紧跟一条断言确保**公共码的 HTTP 状态映射没跟着丢** ——
    // 「删副本」与「删状态登记」是两件事，只盯前者会漏后者。
    assertEquals(8, checked, "新增/删除错误码时必须同步 StudioErrors 与错误码登记");
  }

  @Test
  @DisplayName("公共码（COMMON_*，本模块不再声明副本）仍必须有 HTTP 状态登记")
  void commonCodesStillHaveHttpStatusMapping() {
    assertEquals(
        400, StudioErrors.httpStatusOf(com.bone.core.common.CommonErrorCodes.VALIDATION_FAILED));
    assertEquals(403, StudioErrors.httpStatusOf(com.bone.core.common.CommonErrorCodes.FORBIDDEN));
    assertEquals(
        500, StudioErrors.httpStatusOf(com.bone.core.common.CommonErrorCodes.INTERNAL_ERROR));
    assertEquals(
        409, StudioErrors.httpStatusOf(com.bone.core.common.CommonErrorCodes.IDEMPOTENCY_CONFLICT));
    assertEquals(
        412, StudioErrors.httpStatusOf(com.bone.core.common.CommonErrorCodes.PRECONDITION_FAILED));
  }

  @Test
  @DisplayName("未登记的码必须 fail fast（不落到运行期被兜底成 400/500）")
  void unregisteredCodeFailsFast() {
    assertThrows(
        IllegalStateException.class, () -> StudioErrors.httpStatusOf("EXT_NOT_REGISTERED"));
  }

  @Test
  @DisplayName("工厂用四参构造：errorCode 不得为 null（三参构造会静默丢码）")
  void factoryCarriesErrorCode() {
    BizException ex = StudioErrors.of(StudioErrorCodes.PLUGIN_VERSION_CONFLICT, "9.9.9");
    assertEquals(409, ex.getCode());
    assertEquals("EXT_PLUGIN_VERSION_CONFLICT", ex.getErrorCode());
    assertEquals("EXT_PLUGIN_VERSION_CONFLICT: 9.9.9", ex.getMessage());
  }

  @Test
  @DisplayName("资源不存在类失败必须是 404（改造前被 IllegalArgumentException 压成 400）")
  void notFoundStatus() {
    assertEquals(404, StudioErrors.of(StudioErrorCodes.PLUGIN_NOT_FOUND, 1L).getCode());
    assertEquals(404, StudioErrors.of(StudioErrorCodes.EXT_POINT_NOT_FOUND, 2L).getCode());
    assertEquals(
        404, StudioErrors.of(StudioErrorCodes.PLUGIN_VERSION_NOT_FOUND, "1.0.0").getCode());
    assertEquals(409, StudioErrors.of(StudioErrorCodes.DEPLOY_STATE_INVALID, "未启用").getCode());
    assertEquals(400, StudioErrors.of(StudioErrorCodes.PLUGIN_PACKAGE_INVALID, "非 JAR").getCode());
  }

  @Test
  @DisplayName("advice 必须透传状态与 errorCode（否则被 Exception 兜底成 500 且丢码）")
  void advicePropagatesStatusAndCode() {
    ResponseEntity<ApiResponse<ProblemDetail>> res =
        new StudioWebExceptionHandler()
            .bizException(StudioErrors.of(StudioErrorCodes.PLUGIN_NOT_FOUND, 42L));
    assertEquals(404, res.getStatusCode().value());
    assertEquals("EXT_PLUGIN_NOT_FOUND", res.getBody().getData().getErrorCode());
  }
}
