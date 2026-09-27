package com.bone.integration.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.integration.adapter.web.advice.IntegrationExceptionAdvice;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 错误码 → HTTP 状态 → {@code ProblemDetail.errorCode} 的端到端契约测试。
 *
 * <p><b>为什么必须有这个测试</b>：F1 修的是「前端拿不到 errorCode」——若只用三参 {@code BizException} 构造， {@code errorCode} 会是
 * {@code null}，{@code IntegrationExceptionAdvice} 会把它兜底成 {@code
 * COMMON_VALIDATION_FAILED}，缺陷照旧且没有任何信号。此处直接调用 advice 断言响应体里的 errorCode， 让「码真的透传到前端」成为可回归的断言。
 */
class IntegrationErrorCodesMappingTest {

  private final IntegrationExceptionAdvice advice = new IntegrationExceptionAdvice();

  @Test
  void everyCodeHasRegisteredHttpStatus() {
    for (String code : declaredCodes()) {
      int status = IntegrationErrors.httpStatusOf(code);
      assertThat(status)
          .as("错误码 %s 的 HTTP 状态", code)
          .isBetween(HttpStatus.BAD_REQUEST.value(), 599);
    }
  }

  @Test
  void ofCarriesErrorCodeAndHttpStatus() {
    BizException ex = IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_FOUND, 42L);

    assertThat(ex.getErrorCode()).isEqualTo(IntegrationErrorCodes.FLOW_NOT_FOUND);
    assertThat(ex.getCode()).isEqualTo(404);
    assertThat(ex.getMessage()).contains(IntegrationErrorCodes.FLOW_NOT_FOUND).contains("42");
  }

  @Test
  void advicePropagatesErrorCodeIntoProblemDetail() {
    BizException ex = IntegrationErrors.of(IntegrationErrorCodes.CONNECTOR_NOT_FOUND, 7L);

    ResponseEntity<ApiResponse<ProblemDetail>> res = advice.bizException(ex);

    assertThat(res.getStatusCode().value()).isEqualTo(404);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getData()).isNotNull();
    assertThat(res.getBody().getData().getErrorCode())
        .isEqualTo(IntegrationErrorCodes.CONNECTOR_NOT_FOUND);
  }

  @Test
  void notImplementedStaysHttp501WithDomainCode() {
    BizException ex = IntegrationErrors.of(IntegrationErrorCodes.CONNECTOR_NOT_IMPLEMENTED, "FTP");

    ResponseEntity<ApiResponse<ProblemDetail>> res = advice.bizException(ex);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    assertThat(res.getBody()).isNotNull();
    assertThat(res.getBody().getData()).isNotNull();
    assertThat(res.getBody().getData().getErrorCode())
        .isEqualTo(IntegrationErrorCodes.CONNECTOR_NOT_IMPLEMENTED);
  }

  @Test
  void unregisteredCodeFailsFast() {
    assertThatThrownBy(() -> IntegrationErrors.httpStatusOf("INT_NOT_REGISTERED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INT_NOT_REGISTERED");
  }

  /** 反射枚举码常量：新增常量忘记登记状态时，{@link #everyCodeHasRegisteredHttpStatus} 立即失败。 */
  private static List<String> declaredCodes() {
    List<String> codes = new java.util.ArrayList<>();
    for (Field field : IntegrationErrorCodes.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
        continue;
      }
      try {
        codes.add((String) field.get(null));
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException("无法读取错误码常量: " + field.getName(), ex);
      }
    }
    assertThat(codes).isNotEmpty();
    return codes;
  }
}
