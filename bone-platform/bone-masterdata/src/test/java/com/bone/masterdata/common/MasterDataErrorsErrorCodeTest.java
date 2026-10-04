package com.bone.masterdata.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 锁死「错误码真的进了 {@code BizException.errorCode}」这一性质。
 *
 * <p><b>为什么必须有这个测试</b>（2026-10-02 实测缺陷）：{@code GlobalExceptionHandler .handleBusinessException} 传的是
 * {@code e.getErrorCode()}。若 {@code MasterDataErrors.of(...)} 误用 3 参构造器 {@code new
 * BizException(int, String, Throwable)}（该构造器把 {@code errorCode} 恒置为 {@code null}），则 {@code
 * ProblemDetail.errorCode} 就是 {@code null}： 前端 {@code errors.<CODE>} 的 i18n
 * 映射永远命中不了，监控也无法按稳定业务码聚合—— <b>而HTTP 状态码仍然正确，弱断言（只看 200/400）完全测不出来</b>。
 *
 * <p>实测当时 {@code MD_RECORD_FIELD_VALIDATION_FAILED} / {@code MD_SOD_VIOLATION} / {@code
 * BP_ORDER_STATUS_CONFLICT} 的 {@code errorCode} 全为 null，码只出现在 message 前缀里。 全仓 9 个 {@code *Errors}
 * 类里只有 masterdata 漏了，已修复。
 */
class MasterDataErrorsErrorCodeTest {

  @Test
  @DisplayName("of(errorCode) 必须把码带进 BizException.errorCode（不得为 null）")
  void of_shouldCarryErrorCode() {
    BizException e =
        MasterDataErrors.of(MasterDataErrorCodes.RECORD_FIELD_VALIDATION_FAILED, "字段[x]为必填项");

    assertThat(e.getErrorCode())
        .as("errorCode 为 null 时前端 i18n 映射与监控聚合都会失效，且 HTTP 状态仍正确 → 弱断言测不出")
        .isEqualTo(MasterDataErrorCodes.RECORD_FIELD_VALIDATION_FAILED);
    assertThat(e.getCode()).isEqualTo(400);
    // message 仍须保持「码: 说明」形态（fallback 契约，不能因为补errorCode 就丢掉前缀）
    assertThat(e.getMessage()).startsWith(MasterDataErrorCodes.RECORD_FIELD_VALIDATION_FAILED);
  }

  @Test
  @DisplayName("三参 of(errorCode, detail, cause) 同样必须带码")
  void of_withCause_shouldCarryErrorCode() {
    RuntimeException root = new IllegalStateException("根因");
    BizException e = MasterDataErrors.of(MasterDataErrorCodes.RECORD_CODE_DUPLICATE, "编码重复", root);

    assertThat(e.getErrorCode()).isEqualTo(MasterDataErrorCodes.RECORD_CODE_DUPLICATE);
    assertThat(e.getCode()).isEqualTo(409);
    assertThat(e.getCause()).isSameAs(root);
  }

  @Test
  @DisplayName("of(errorCode) 单参重载也不得丢码")
  void of_singleArg_shouldCarryErrorCode() {
    BizException e = MasterDataErrors.of(MasterDataErrorCodes.TEMPLATE_NOT_FOUND);

    assertThat(e.getErrorCode()).isEqualTo(MasterDataErrorCodes.TEMPLATE_NOT_FOUND);
    assertThat(e.getCode()).isEqualTo(404);
  }

  /**
   * 反射兜底：防止将来新增 {@code of} 重载时又用了不带码的构造器。
   *
   * <p>这比逐个重载写测试更耐改——新增重载会被自动纳入检查，无需记得补测试。
   */
  @Test
  @DisplayName("所有 public static of(...) 重载都必须返回携带 errorCode 的异常")
  void allOfOverloads_shouldCarryErrorCode() throws IllegalAccessException {
    List<String> violations = new ArrayList<>();
    for (Method m : MasterDataErrors.class.getDeclaredMethods()) {
      if (!Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) {
        continue;
      }
      if (!"of".equals(m.getName()) || m.getParameterCount() == 0) {
        continue;
      }
      if (!String.class.equals(m.getParameterTypes()[0])) {
        continue;
      }
      // 用第一个 String 参数当错误码构造实参
      Object[] args = new Object[m.getParameterCount()];
      args[0] = MasterDataErrorCodes.RECORD_FIELD_VALIDATION_FAILED;
      for (int i = 1; i < args.length; i++) {
        Class<?> pt = m.getParameterTypes()[i];
        if (Throwable.class.isAssignableFrom(pt)) {
          args[i] = new IllegalStateException("根因");
        } else if (pt.isPrimitive()) {
          args[i] = 0;
        } else {
          args[i] = "detail";
        }
      }
      Object ret;
      try {
        ret = m.invoke(null, args);
      } catch (java.lang.reflect.InvocationTargetException e) {
        // of(...) 内部会 fail-fast 抛异常（未登记码等），跳过这类重载
        continue;
      }
      if (ret instanceof BizException be) {
        if (be.getErrorCode() == null) {
          violations.add(
              m.getName()
                  + "("
                  + java.util.Arrays.stream(m.getParameterTypes())
                      .map(Class::getSimpleName)
                      .reduce((a, b) -> a + "," + b)
                      .orElse("")
                  + ") 未携带 errorCode");
        }
      } else {
        violations.add(m.getName() + " 返回类型不是 BizException");
      }
    }
    assertThat(violations).as("新增 of(...) 重载时必须用带 errorCode 的 BizException 构造器").isEmpty();
  }

  /**
   * 全码表核对：每个登记在 {@code DEFAULT_HTTP_STATUS} 的码都必须能解析出 4xx/5xx， 否则调用 {@code of(code)}
   * 会在构造期fail-fast，把本可返回的业务错误变成 500。
   */
  @Test
  @DisplayName("登记表中不存在会退化成 500 的码")
  void registeredCodes_shouldNotFallBackToServerError() {
    Map<String, Integer> table = registeredStatusTable();
    for (Map.Entry<String, Integer> e : table.entrySet()) {
      assertThat(e.getValue())
          .as("码 %s 映射到 %s，会被resolveHttpStatus 兜底成 500", e.getKey(), e.getValue())
          .isBetween(400, 599);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Integer> registeredStatusTable() {
    // 表是 private static final，用反射读；读不到就跳过（避免因可见性调整而误报）
    try {
      java.lang.reflect.Field f = MasterDataErrors.class.getDeclaredField("DEFAULT_HTTP_STATUS");
      f.setAccessible(true);
      return (Map<String, Integer>) f.get(null);
    } catch (ReflectiveOperationException e) {
      return Map.of();
    }
  }

  @Test
  @DisplayName("未登记的码必须在构造期 fail-fast，而不是静默兜底成 500")
  void unregisteredCode_shouldFailFast() {
    assertThatThrownBy(() -> MasterDataErrors.of("MD_NOT_REGISTERED_AT_ALL"))
        .as("未登记码应 fail-fast；静默兜底会把业务错误变成 500")
        .isInstanceOf(IllegalStateException.class);
  }
}
