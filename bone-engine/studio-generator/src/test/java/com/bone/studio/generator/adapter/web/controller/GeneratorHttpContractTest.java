package com.bone.studio.generator.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.studio.generator.common.GeneratorErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * C 段联调：G-2 响应信封收敛 与 G-3 错误码独立字段 的<b>真实 HTTP</b> 验收（{@code RANDOM_PORT} + {@code
 * TestRestTemplate}，走完整 Servlet 栈与全局异常翻译，非 MockMvc 短路）。
 *
 * <p>只锁两条最容易静默回退的契约：
 *
 * <ol>
 *   <li>能力端点返回的是 core 信封（带 {@code code}/{@code timestamp}），不是模块私有 {@code ApiResponse}
 *   <li>业务异常带独立 {@code errorCode} 落到响应体，前端 {@code i18n.t('errors.' + errorCode)} 才有分支可命中
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeneratorHttpContractTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  /** G-2：能力端点必须返回 core 信封——带 {@code code} 与 {@code timestamp} 两个本地类没有的字段。 */
  @Test
  void capabilitiesReturnsCoreEnvelope() {
    ResponseEntity<String> resp =
        restTemplate.getForEntity(url("/api/v1/generator/capabilities"), String.class);

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    String body = resp.getBody();
    assertTrue(body != null && body.contains("\"success\""), "信封缺少 success: " + body);
    assertTrue(body.contains("\"timestamp\""), "非 core 信封（缺 timestamp）: " + body);
  }

  /** G-3：模板不存在 → HTTP 404 且响应体带独立 errorCode（修复前 errorCode 恒为 null）。 */
  @Test
  void templateNotFoundCarriesErrorCodeOverHttp() {
    ResponseEntity<String> resp =
        restTemplate.getForEntity(url("/api/v1/generator/templates/9999999"), String.class);

    assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    String body = resp.getBody();
    assertTrue(
        body != null && body.contains(GeneratorErrorCodes.TEMPLATE_NOT_FOUND),
        "响应体未透出 errorCode: " + body);
  }
}
