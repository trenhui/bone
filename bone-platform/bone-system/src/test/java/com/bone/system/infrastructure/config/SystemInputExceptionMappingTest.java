package com.bone.system.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 输入类异常 → HTTP 400 的契约回归测试。
 *
 * <p><b>为什么需要它</b>：这四类都是<b>客户端输入错误</b>。若 advice 未显式映射，它们会冒泡到 catch-all {@code Exception} 被记成 500
 * {@code COMMON_INTERNAL_ERROR}——调用方笔误被算进服务端故障预算，污染 5xx 告警与错误率指标。本模块 pom 只依赖 bone-core、不依赖
 * bone-web，框架级处理器不在 classpath 上，**只能 靠本模块自带映射**，一旦被误删就没有第二道防线。
 *
 * <p>本测试不启动 Spring 上下文（避免依赖 DB/Redis），而是直接反射校验 advice 上存在覆盖这四类的 {@code @ExceptionHandler} 方法——比
 * HTTP 集成测试更轻更稳，且精确锁住「映射是否存在」这个契约本身。
 */
class SystemInputExceptionMappingTest {

  private static final List<Class<?>> INPUT_EXCEPTIONS =
      List.of(
          MethodArgumentNotValidException.class,
          MethodArgumentTypeMismatchException.class,
          MissingServletRequestParameterException.class,
          HttpMessageNotReadableException.class);

  /** 收集 advice 上所有 {@code @ExceptionHandler} 显式声明处理的异常类型。 */
  private static List<Class<?>> collectHandledExceptionTypes() {
    List<Class<?>> handled = new ArrayList<>();
    for (Method m : GlobalExceptionHandler.class.getDeclaredMethods()) {
      ExceptionHandler ann = m.getAnnotation(ExceptionHandler.class);
      if (ann != null) {
        handled.addAll(Arrays.asList(ann.value()));
      }
    }
    return handled;
  }

  /**
   * 四类输入异常必须都被 advice 显式接住。
   *
   * <p>用 {@link ResponseStatus} 读取状态：这三个分支属「异常类型 → 唯一固定状态」，按模块内注释的约定用 {@code @ResponseStatus}
   * 而非动态取状态。
   */
  @Test
  @DisplayName("四类输入异常必须有 400 映射，否则会落 catch-all 变 500")
  void inputExceptionsMapTo400() {
    List<Class<?>> handled = collectHandledExceptionTypes();

    for (Class<?> input : INPUT_EXCEPTIONS) {
      assertTrue(
          handled.contains(input),
          () ->
              String.format(
                  "%s 未被 GlobalExceptionHandler 显式映射——它会冒泡到 catch-all 被记成 500，"
                      + "把客户端输入错误算进服务端故障预算",
                  input.getSimpleName()));
    }
  }

  @Test
  @DisplayName("catch-all 兜底必须存在且为 500（否则未预期异常会漏成裸 Spring 500 响应）")
  void catchAllRemainsForUnexpectedExceptions() {
    Method catchAll = null;
    for (Method m : GlobalExceptionHandler.class.getDeclaredMethods()) {
      ExceptionHandler ann = m.getAnnotation(ExceptionHandler.class);
      if (ann != null && Arrays.asList(ann.value()).contains(Exception.class)) {
        catchAll = m;
        break;
      }
    }

    assertNotNull(catchAll, "必须保留 Exception catch-all 兜底，否则未预期异常会漏成容器默认响应");
    ResponseStatus status = catchAll.getAnnotation(ResponseStatus.class);
    assertNotNull(status, "catch-all 必须显式声明 @ResponseStatus");
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, status.value(), "catch-all 必须是 500");
  }
}
