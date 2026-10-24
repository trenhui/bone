package com.bone.engine.extension.studio.config;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.application.support.StudioCommandResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 请求媒体类型不被支持 → 415（统一 {@code ApiResponse} 信封）。
 *
 * <p><b>为什么必须独立于 {@link StudioWebExceptionHandler}：作用域。</b>
 *
 * <p>上传端点（{@code POST /api/v1/extension/plugins:upload}）声明了 {@code consumes =
 * MULTIPART_FORM_DATA_VALUE}。调用方发 {@code application/json} 时，Spring 在 {@code
 * RequestMappingInfoHandlerMapping#handleNoMatch} 抛出 {@code
 * HttpMediaTypeNotSupportedException}——这一步发生在<b>路由匹配阶段</b>，此刻<b>还没有处理方法</b>。
 *
 * <p>{@link StudioWebExceptionHandler} 带 {@code basePackages} 限定，{@code
 * ExceptionHandlerExceptionResolver} 需要借处理方法所在的包来决定命中哪个 advice； 处理方法是 {@code null} 时限定作用域的 advice
 * 匹配不上，异常继续冒泡，最终由容器渲染成 Spring 默认错误体
 *
 * <pre>
 * {"timestamp":...,"status":415,"error":"Unsupported Media Type","path":...}
 * </pre>
 *
 * <p>它既没有 {@code errorCode}，也不满足统一响应信封（HC-003）。 本类<b>不限定 basePackages</b>，因此能在这个阶段命中。
 *
 * <p><b>为何只声明这一种异常</b>：本类不设作用域，为了避免与 {@link StudioWebExceptionHandler} 争抢其他异常的归属，
 * 这里只接管「路由匹配阶段」才可能出现、且限定作用域的 advice 必然接不到的那一类。 参数绑定阶段抛出的 {@code MultipartException} 仍有处理方法，归 {@link
 * StudioWebExceptionHandler} 处理。
 *
 * <p>415 是<b>客户端请求形态错误</b>，绝不能与 5xx 混为一谈：不接管会被记成 {@code COMMON_INTERNAL_ERROR}， 把调用方笔误算进服务端故障预算。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class StudioMediaTypeNotSupportedHandler {

  private static final Logger log =
      LoggerFactory.getLogger(StudioMediaTypeNotSupportedHandler.class);

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<ProblemDetail>> mediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex) {
    log.warn("[API] 不支持的请求媒体类型: {}", ex.getContentType());
    return StudioCommandResponses.problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, CommonErrorCodes.UNSUPPORTED_MEDIA_TYPE, "请求媒体类型不被支持");
  }
}
