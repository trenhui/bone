package com.bone.metadata.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 输入类异常 → HTTP 400 的契约回归测试（module 自带 advice）。
 *
 * <p><b>为什么需要它</b>：这四类都是<b>客户端输入错误</b>。若 advice 未显式映射，它们会冒泡到 {@code Exception} catch-all 被记成 <b>500
 * COMMON_INTERNAL_ERROR</b>——调用方笔误被算进服务端故障预算， 污染 5xx 告警与错误率指标。
 *
 * <p><b>2026-10-20 全模块联调实测补录</b>：{@code GET /api/v1/metadata/fields/dict-bindings} 不传必填 {@code
 * typeCode} 时返回 500，而 bone-system / bone-masterdata / bone-file 三模块对同类缺参统一 400 —— 跨模块口径不一致。 根因是本
 * advice 显式接管了 {@link MethodArgumentTypeMismatchException}（参数"传错类型"）却漏了 {@link
 * MissingServletRequestParameterException}（参数"根本没传"）。本测试把这两类绑在一起断言， 防止以后只修一半又出现同类漂移。
 *
 * <p>不启动 Spring 上下文（避免依赖 DB/Redis）：前三个用例直接 new advice 调用 handler，精确锁住「状态 + errorCode」；
 * 第四个用例用反射校验映射清单完整。
 */
class MetadataInputExceptionMappingTest {

  /** 四类输入异常：必须全部被本模块 advice 显式接住，否则落 catch-all 变 500。 */
  private static final List<Class<?>> INPUT_EXCEPTIONS =
      List.of(
          MethodArgumentNotValidException.class,
          MethodArgumentTypeMismatchException.class,
          MissingServletRequestParameterException.class,
          HttpMessageNotReadableException.class);

  @Test
  @DisplayName("必填查询参数缺失 → 400 MALFORMED_REQUEST（不是 500：客户端笔误不该计入服务端故障预算）")
  void missingRequestParamMapsTo400() {
    ResponseEntity<ApiResponse<ProblemDetail>> resp =
        new GlobalExceptionHandler()
            .handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("typeCode", "String"));

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    ProblemDetail problem = resp.getBody().getData();
    assertEquals(CommonErrorCodes.MALFORMED_REQUEST, problem.getErrorCode());
    assertTrue(
        problem.getDetail().contains("typeCode"),
        () -> "detail 应回显缺失的参数名，便于调用方定位: " + problem.getDetail());
  }

  @Test
  @DisplayName("参数类型不匹配 → 400 MALFORMED_REQUEST（与缺参同档，同属客户端输入错误）")
  void typeMismatchMapsTo400() {
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException(
            null, Long.class, "id", null, new RuntimeException());
    ResponseEntity<ApiResponse<ProblemDetail>> resp =
        new GlobalExceptionHandler().handleTypeMismatch(ex);

    assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    assertEquals(CommonErrorCodes.MALFORMED_REQUEST, resp.getBody().getData().getErrorCode());
  }

  @Test
  @DisplayName("四类输入异常必须全部有显式映射；Exception catch-all 仍保留 500")
  void allInputExceptionsHandled() {
    List<Class<?>> handled = new ArrayList<>();
    boolean hasCatchAll = false;
    for (Method m : GlobalExceptionHandler.class.getDeclaredMethods()) {
      ExceptionHandler ann = m.getAnnotation(ExceptionHandler.class);
      if (ann == null) {
        continue;
      }
      handled.addAll(Arrays.asList(ann.value()));
      if (Arrays.asList(ann.value()).contains(Exception.class)) {
        hasCatchAll = true;
      }
    }
    for (Class<?> input : INPUT_EXCEPTIONS) {
      assertTrue(
          handled.contains(input),
          () ->
              String.format(
                  "%s 未被 GlobalExceptionHandler 显式映射——它会冒泡到 catch-all 被记成 500，"
                      + "把客户端输入错误算进服务端故障预算",
                  input.getSimpleName()));
    }
    assertTrue(hasCatchAll, "必须保留 Exception catch-all 兜底，否则未预期异常会漏成容器默认响应");
  }
}
