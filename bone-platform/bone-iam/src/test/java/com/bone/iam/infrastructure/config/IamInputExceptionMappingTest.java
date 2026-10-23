package com.bone.iam.infrastructure.config;

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * IAM 输入类异常 → HTTP 400 的契约回归测试（module 自带 advice）。
 *
 * <p><b>为何本模块必须自带</b>：{@code IamExceptionHandler} 与 bone-web 框架 {@code GlobalExceptionHandler}
 * 并存且本模块 advice 优先（详见该类注释），框架基类的同名分支对本模块<b>不生效</b>， 一旦本类漏映射就没有第二道防线。
 *
 * <p><b>2026-10-20 全模块联调实测补录</b>：{@code GET /api/v1/iam/sso/callback} 不传必填 {@code code} 时返回 <b>500
 * COMMON_INTERNAL_ERROR</b>；而 bone-system / bone-masterdata / bone-file 三模块对同类缺参统一 400。 根因是本 advice
 * 已接管 {@link MethodArgumentTypeMismatchException}（参数"传错类型"）却漏了 {@link
 * MissingServletRequestParameterException}（参数"根本没传"）。本测试把两类绑在一起断言，防止只修一半再次漂移。
 */
class IamInputExceptionMappingTest {

  private static final List<Class<?>> INPUT_EXCEPTIONS =
      List.of(
          MethodArgumentNotValidException.class,
          MethodArgumentTypeMismatchException.class,
          MissingServletRequestParameterException.class,
          HttpMessageNotReadableException.class);

  /** handler 的日志分支会取 request.getRequestURI()，故用 spring-test 的 mock 请求（非 null）。 */
  private static MockHttpServletRequest request() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/v1/iam/sso/callback");
    return req;
  }

  /**
   * 必填查询参数缺失 → 400（不是 500：客户端笔误不该计入服务端故障预算）。
   *
   * <p><b>errorCode 取 {@code VALIDATION_FAILED} 而非 {@code MALFORMED_REQUEST}</b>：跨服务异常落点一致性
   * 门禁（[9d/35]）要求同一异常类型在各服务映射到同一 (status, errorCode)——实测 generator / file / masterdata 与 bone-web
   * 框架基类对该异常统一是 400+VALIDATION_FAILED。这是错误码登记 §2「同一错误语义跨服务 必须同码」的机器判据：取 MALFORMED_REQUEST 会让前端 i18n
   * 与监控聚合裂成两个口径。
   */
  @Test
  @DisplayName("必填查询参数缺失 → 400 VALIDATION_FAILED（不是 500；且与兄弟服务同码）")
  void missingRequestParamMapsTo400() {
    ResponseEntity<ApiResponse<ProblemDetail>> resp =
        new IamExceptionHandler()
            .handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("code", "String"), request());

    assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
    ProblemDetail problem = resp.getBody().getData();
    assertEquals(CommonErrorCodes.VALIDATION_FAILED, problem.getErrorCode());
    assertTrue(
        problem.getDetail().contains("code"),
        () -> "detail 应回显缺失的参数名，便于调用方定位: " + problem.getDetail());
  }

  @Test
  @DisplayName("参数类型不匹配 → 400 MALFORMED_REQUEST（与缺参同档）")
  void typeMismatchMapsTo400() {
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException(
            null, Long.class, "id", null, new RuntimeException());
    ResponseEntity<ApiResponse<ProblemDetail>> resp =
        new IamExceptionHandler().handleTypeMismatch(ex, request());

    assertEquals(HttpStatus.BAD_REQUEST.value(), resp.getStatusCode().value());
    assertEquals(CommonErrorCodes.MALFORMED_REQUEST, resp.getBody().getData().getErrorCode());
  }

  @Test
  @DisplayName("四类输入异常必须全部有显式映射；Exception catch-all 仍保留 500")
  void allInputExceptionsHandled() {
    List<Class<?>> handled = new ArrayList<>();
    boolean hasCatchAll = false;
    for (Method m : IamExceptionHandler.class.getDeclaredMethods()) {
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
                  "%s 未被 IamExceptionHandler 显式映射——它会冒泡到 catch-all 被记成 500，" + "把客户端输入错误算进服务端故障预算",
                  input.getSimpleName()));
    }
    assertTrue(hasCatchAll, "必须保留 Exception catch-all 兜底，否则未预期异常会漏成容器默认响应");
  }
}
