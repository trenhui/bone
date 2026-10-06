package com.bone.blueprint.adapter.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.blueprint.adapter.web.assembler.PaymentAssembler;
import com.bone.blueprint.application.PaymentApplicationService;
import com.bone.core.exception.GlobalExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 支付回调「来源 IP 白名单」的契约测试（standalone MockMvc + mock 应用层，不启动容器、不连库）。
 *
 * <p><b>为什么补它</b>：这段逻辑此前<strong>零覆盖</strong>（jacoco 显示 4 个分支全未命中），而它恰恰是最容易静默失效的一处—— 白名单键名曾写成 {@code
 * bone.payment.callback.allowed-source-ips}（yml 定义在 {@code bone.blueprint.payment.} 下），占位符带默认值 ⇒
 * <strong>静默回落为空串＝不限制来源</strong>，白名单形同虚设且无任何报错。
 *
 * <p>本测试锁住三件事：
 *
 * <ol>
 *   <li>白名单<strong>为空</strong>时不限制来源（默认形态，便于联调）——不是"拒绝一切"；
 *   <li>命中白名单才放行；{@code X-Forwarded-For} 按 {@code ,} 分割并 trim（多段 / 空格都不是漏网之鱼）——
 *       但<b>取哪一段是缺陷而非契约</b>，见 {@link #forgedForwardedHeaderCurrentlyBypassesAllowlist()}；
 *   <li>未命中必须 {@code 403} + 错误码 {@code
 *       BP_PAYMENT_CALLBACK_SOURCE_NOT_ALLOWED}，且<strong>不得进入应用层</strong>
 *       ——只断言状态码的话，"先执行再判来源"的实现也能通过。
 * </ol>
 *
 * <p>键名本身由 {@link com.bone.blueprint.ConfigKeysContractTest} 守护（源码引用的 {@code bone.*} 占位符必须在 yml
 * 有定义）。
 */
class PaymentControllerCallbackSourceTest {

  private static final String CALLBACK_BODY =
      """
      {"paymentId": 1, "channelTradeNo": "CHN-1", "paidAmount": 10.00, "signature": "sig", "success": true}
      """;

  private final PaymentApplicationService paymentApplicationService =
      mock(PaymentApplicationService.class);
  private final PaymentAssembler paymentAssembler = mock(PaymentAssembler.class);

  /** 白名单为空 = 不限制来源（默认形态）。 */
  @Test
  void blankAllowlistPassesThrough() throws Exception {
    mockMvc("")
        .perform(callbackRequest().header("X-Forwarded-For", "8.8.8.8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(paymentApplicationService).processCallback(any());
  }

  /**
   * 命中白名单即放行（配置项允许逗号后带空格，须 trim）。
   *
   * <p><b>2026-10-05 更名</b>：原名 {@code matchedSourcePassesAndUsesFirstForwardedHop} 把「采信 XFF 首段」
   * 写成了契约，而它是<b>缺陷</b>——XFF 头可由任意客户端构造。本用例现在只声明"分割与 trim"这部分语义， 不再声称"首段"是正确行为。
   */
  @Test
  void matchedSourcePasses() throws Exception {
    mockMvc("10.0.0.1, 10.0.0.2")
        .perform(callbackRequest().header("X-Forwarded-For", "10.0.0.2, 1.1.1.1"))
        .andExpect(status().isOk());

    verify(paymentApplicationService).processCallback(any());
  }

  /**
   * 兼容形态记录：<b>未配置可信代理</b>时，XFF 仍被无条件采信 ⇒ 自造 XFF 可绕过白名单。
   *
   * <p>已配 {@code trusted-proxy-ips} 的形态由 {@link
   * #forgedForwardedHeaderIsIgnoredWhenNotFromTrustedProxy()}
   * 覆盖，那条才是生产形态。本条留作对照，说明「配没配可信代理」是安全性的分水岭。
   */
  @Test
  void forgedForwardedHeaderPassesWhenNoTrustedProxyConfigured() throws Exception {
    mockMvc("10.0.0.9", "")
        .perform(
            callbackRequest()
                .with(
                    request -> {
                      request.setRemoteAddr("172.16.0.5");
                      return request;
                    })
                .header("X-Forwarded-For", "10.0.0.9"))
        .andExpect(status().isOk());
  }

  /**
   * 生产形态：请求来自<b>非可信代理</b>（绕过网关直连）且自造 XFF ⇒ 该 XFF 作数，来源按 remoteAddr 判⇒ 403。
   *
   * <p>这是本次加固的核心断言：没有它，「配了可信代理」只是配置项存在，拦不住任何攻击。
   */
  @Test
  void forgedForwardedHeaderIsIgnoredWhenNotFromTrustedProxy() throws Exception {
    mockMvc("10.0.0.9", "172.31.0.1")
        .perform(
            callbackRequest()
                .with(
                    request -> {
                      // 绕过网关直连：remoteAddr 不是可信代理
                      request.setRemoteAddr("172.16.0.5");
                      return request;
                    })
                .header("X-Forwarded-For", "10.0.0.9"))
        .andExpect(status().isForbidden());
  }

  /**
   * 生产形态正向：请求来自可信代理（remoteAddr 命中）⇒ 采信 XFF，取**最左的非可信代理**地址。
   *
   * <p>「最左非可信」而非「首段」：XFF 形如「真实渠道, 代理1」，代理1 是自家网关 ⇒ 跳过它。
   */
  @Test
  void xffFromTrustedProxyYieldsLeftmostUntrustedHop() throws Exception {
    mockMvc("203.0.113.9", "172.31.0.1")
        .perform(
            callbackRequest()
                .with(
                    request -> {
                      request.setRemoteAddr("172.31.0.1");
                      return request;
                    })
                .header("X-Forwarded-For", "203.0.113.9, 172.31.0.1"))
        .andExpect(status().isOk());
  }

  /** 可信代理自己伪造 XFF 把自己写成来源时，因其仍在可信列表内被跳过，最终回落到 remoteAddr。 */
  @Test
  void allTrustedHopsFallBackToRemoteAddr() throws Exception {
    mockMvc("172.31.0.1", "172.31.0.1")
        .perform(
            callbackRequest()
                .with(
                    request -> {
                      request.setRemoteAddr("172.31.0.1");
                      return request;
                    })
                .header("X-Forwarded-For", "172.31.0.1"))
        .andExpect(status().isOk());
  }

  /** 无 {@code X-Forwarded-For} 时回落到 {@code remoteAddr}。 */
  @Test
  void fallsBackToRemoteAddrWhenNoForwardedHeader() throws Exception {
    mockMvc("203.0.113.7")
        .perform(
            callbackRequest()
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.7");
                      return request;
                    }))
        .andExpect(status().isOk());

    verify(paymentApplicationService).processCallback(any());
  }

  /**
   * 未命中白名单：{@code 403} + 业务错误码，且不得进入应用层。
   *
   * <p>回归防护：若把来源判断写成"先执行用例再判来源"，本用例的 {@code never()} 断言会立刻失败。
   */
  @Test
  void unmatchedSourceIsRejectedBeforeApplication() throws Exception {
    mockMvc("10.0.0.1")
        .perform(callbackRequest().header("X-Forwarded-For", "8.8.8.8"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value(Matchers.containsString("BP_PAYMENT_CALLBACK_SOURCE_NOT_ALLOWED")));

    verify(paymentApplicationService, never()).processCallback(any());
  }

  private MockMvc mockMvc(String allowedSourceIpsRaw) {
    return mockMvc(allowedSourceIpsRaw, "");
  }

  private MockMvc mockMvc(String allowedSourceIpsRaw, String trustedProxyIpsRaw) {
    PaymentController controller =
        new PaymentController(paymentApplicationService, paymentAssembler);
    // @Value 字段在standalone 装配下不会被注入，显式设值（与三个 schedule Job 的测试同一手法）
    ReflectionTestUtils.setField(controller, "allowedSourceIpsRaw", allowedSourceIpsRaw);
    ReflectionTestUtils.setField(controller, "trustedProxyIpsRaw", trustedProxyIpsRaw);
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      callbackRequest() {
    return post("/api/v1/payments/callback")
        .contentType(MediaType.APPLICATION_JSON)
        .content(CALLBACK_BODY);
  }
}
