package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 全局异常翻译回归测试。
 *
 * <p>修复前 studio-generator 未依赖 bone-web，{@code com.bone.core.exception.GlobalExceptionHandler} 这个
 * {@code @RestControllerAdvice} 从未被注册为 Bean——业务异常（如 {@code BizException(404)}）未经翻译 直接落到容器，被兜底成 HTTP
 * 500；且 404/405 语义与平台其它模块不一致。
 *
 * <p>本测试锁定修复后的契约：业务异常按异常自带 code 翻译（404→404，而非 500）， 方法不被支持返回 405（而非被吞成 500）。若未来有人移除 bone-web
 * 依赖或误加本地 catch-all 兜底， 这里会立刻由 500 复现而红灯。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalExceptionHandlerTranslationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private String url(long id) {
    return "http://localhost:" + port + "/api/v1/generator/templates/" + id;
  }

  /** 不存在的模板 → 业务异常 BizException(404) 必须翻译成 HTTP 404，而不是被兜底成 500。 */
  @Test
  void notFoundTemplateReturnsHttp404Not500() {
    ResponseEntity<String> resp = restTemplate.getForEntity(url(9_999_999L), String.class);
    assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode(), "BizException(404) 应翻译为 404");
  }

  /** 对只读（GET）端点使用 POST → 必须返回 405，而不是被 catch-all 兜底成 500。 */
  @Test
  void wrongMethodReturnsHttp405Not500() {
    ResponseEntity<String> resp =
        restTemplate.exchange(
            url(1L), HttpMethod.POST, new HttpEntity<>(new byte[0]), String.class);
    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode(), "方法不被支持应返回 405");
  }
}
