package com.bone.engine.extension.studio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.domain.gateway.ReporterTokenValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 数据面上报令牌校验（{@link ReporterTokenFilter}）的行为锁定。
 *
 * <p>核心不变量：**校验不通过时绝不进业务方法**（不落库）。测试用「filterChain 是否被调用」 断言这一点 —— 只断言状态码不够，因为一个先写库再返回 401 的实现同样能返回
 * 401。
 */
class ReporterTokenFilterTest {

  private static final String INGEST = ReporterTokenFilter.INGEST_PATH_COLON;
  private static final String INGEST_SLASH = ReporterTokenFilter.INGEST_PATH_SLASH;
  private static final String TOKEN = "s3cr3t-reporter-token";

  /** 已配置密钥、且只接受 expected —— 正常生产形态。 */
  private static ReporterTokenFilter filterAccepting(String expected) {
    return new ReporterTokenFilter(fixedValidator(true, expected));
  }

  /**
   * 构造一个 validator桩。
   *
   * <p>注意：{@link ReporterTokenValidator} 有两个抽象方法，<b>不是函数式接口</b>， 不能用 lambda 写。
   *
   * @param configured 服务端是否已配置密钥（决定 503 vs 401）
   * @param expected 唯一可接受的令牌值；configured=false 时忽略
   */
  private static ReporterTokenValidator fixedValidator(boolean configured, String expected) {
    return new ReporterTokenValidator() {
      @Override
      public boolean isAcceptable(@Nullable String presented) {
        return configured && expected.equals(presented);
      }

      @Override
      public boolean isConfigured() {
        return configured;
      }
    };
  }

  @Test
  @DisplayName("令牌匹配 → 放行进业务方法")
  void allowsWhenTokenMatches() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    req.addHeader(ReporterTokenFilter.HEADER, TOKEN);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertEquals(HttpStatus.OK.value(), resp.getStatus());
    assertTrue(chain.getRequest() != null, "filterChain 必须被调用（请求已进业务方法）");
  }

  @Test
  @DisplayName("令牌不匹配 → 401 且**不进业务方法**（失败关闭，不落库）")
  void rejectsAndShortCircuitsWhenTokenMismatch() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    req.addHeader(ReporterTokenFilter.HEADER, "wrong-token");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatus());
    assertEquals(null, chain.getRequest(), "令牌不匹配时 filterChain 绝不能被调用");
    assertEquals("EXTENSION_REPORTER_TOKEN_INVALID", errorCodeOf(resp));
  }

  @Test
  @DisplayName("缺令牌头 → 401（不回落为放行）")
  void rejectsWhenTokenHeaderAbsent() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatus());
    assertEquals(null, chain.getRequest());
  }

  @Test
  @DisplayName("空串令牌视为缺令牌 → 401（防「空值绕过」）")
  void rejectsBlankToken() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    req.addHeader(ReporterTokenFilter.HEADER, "");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatus());
    assertEquals(null, chain.getRequest());
  }

  @Test
  @DisplayName("服务端未配置密钥且要求校验 → 503 且**不进业务方法**（失败关闭，默认行为）")
  void rejectsWith503WhenServerTokenNotConfigured() throws Exception {
    ReporterTokenFilter unconfigured = new ReporterTokenFilter(fixedValidator(false, null));
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    // 上报方主动带了令牌也照样拒绝：服务端没密钥可比对，此时放行等于恢复匿名可写。
    req.addHeader(ReporterTokenFilter.HEADER, TOKEN);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    unconfigured.doFilter(req, resp, chain);

    assertEquals(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        resp.getStatus(),
        "服务端未配置密钥属部署问题，必须答 503（可让上报方立刻发现日志没上报）");
    assertEquals(null, chain.getRequest(), "服务端未配置密钥时 filterChain 绝不能被调用");
    assertEquals("EXTENSION_REPORTER_TOKEN_NOT_CONFIGURED", errorCodeOf(resp));
  }

  @Test
  @DisplayName("503 与 401 必须可区分（前者改部署配置，后者查上报方密钥）")
  void distinguishesServerMisconfigFromClientAuthFailure() throws Exception {
    MockHttpServletResponse unconfigured = new MockHttpServletResponse();
    new ReporterTokenFilter(fixedValidator(false, null))
        .doFilter(new MockHttpServletRequest("POST", INGEST), unconfigured, new MockFilterChain());

    MockHttpServletResponse mismatched = new MockHttpServletResponse();
    MockHttpServletRequest withWrongToken = new MockHttpServletRequest("POST", INGEST);
    withWrongToken.addHeader(ReporterTokenFilter.HEADER, "nope");
    filterAccepting(TOKEN).doFilter(withWrongToken, mismatched, new MockFilterChain());

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), unconfigured.getStatus(), "未配置 => 503");
    assertEquals(HttpStatus.UNAUTHORIZED.value(), mismatched.getStatus(), "令牌错 => 401");
    assertEquals(
        "EXTENSION_REPORTER_TOKEN_NOT_CONFIGURED",
        errorCodeOf(unconfigured),
        "两种失败的错误码必须不同，否则上报方无法定位该改哪一侧");
  }

  @Test
  @DisplayName("斜杠别名路径同样受管控（两个映射都permitAll，漏一个就是绕过）")
  void guardsSlashAliasPathToo() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST_SLASH);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatus());
    assertEquals(null, chain.getRequest());
  }

  @Test
  @DisplayName("非上报路径不受影响（控制面端点不能被这个过滤器误拦）")
  void passesThroughOtherPaths() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/extension/plugins");
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertTrue(chain.getRequest() != null, "控制面端点必须继续走后续过滤器");
  }

  @Test
  @DisplayName("上报路径的GET 请求不拦截（本过滤器只管上报通道的 POST）")
  void ignoresNonPostOnIngestPath() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", INGEST);
    MockHttpServletResponse resp = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filterAccepting(TOKEN).doFilter(req, resp, chain);

    assertTrue(chain.getRequest() != null);
  }

  @Test
  @DisplayName("拒绝响应是可解析 JSON 且带 no-store（前端/监控可据此区分令牌错与业务失败）")
  void rejectionBodyIsMachineReadable() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    MockHttpServletResponse resp = new MockHttpServletResponse();

    filterAccepting(TOKEN).doFilter(req, resp, new MockFilterChain());

    assertTrue(
        resp.getContentType() != null
            && resp.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE),
        "拒绝响应必须是 JSON，实际: " + resp.getContentType());
    assertEquals("no-store", resp.getHeader(HttpHeaders.CACHE_CONTROL));
    assertEquals("EXTENSION_REPORTER_TOKEN_INVALID", errorCodeOf(resp));
  }

  @Test
  @DisplayName("显式放开（reporter-auth-required=false 的本地联调态）→ 放行")
  void allowsWhenExplicitlyRelaxed() throws Exception {
    // 本地联调的唯一合法形态：服务端确实未配置密钥，且运维显式声明"我知道这在放行"。
    ReporterTokenFilter relaxed =
        new ReporterTokenFilter(
            new ReporterTokenValidator() {
              @Override
              public boolean isAcceptable(@Nullable String presented) {
                return true; // 仅可能发生在 required=false 且未配置密钥时
              }

              @Override
              public boolean isConfigured() {
                return false;
              }
            });
    MockHttpServletRequest req = new MockHttpServletRequest("POST", INGEST);
    MockFilterChain chain = new MockFilterChain();

    relaxed.doFilter(req, new MockHttpServletResponse(), chain);

    assertTrue(chain.getRequest() != null);
  }

  private static String errorCodeOf(MockHttpServletResponse resp) throws Exception {
    String body = resp.getContentAsString();
    int idx = body.indexOf("\"code\":\"");
    if (idx < 0) {
      return null;
    }
    int start = idx + "\"code\":\"".length();
    return body.substring(start, body.indexOf('"', start));
  }
}
