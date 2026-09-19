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
 *   <li>命中白名单才放行，且 {@code X-Forwarded-For} 取<strong>首段</strong>并按 {@code ,} 分割后 trim（多段 / 空格都不是
 *       漏网之鱼）；
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

  /** 命中白名单：{@code X-Forwarded-For} 取首段，且配置项允许逗号后带空格（须 trim）。 */
  @Test
  void matchedSourcePassesAndUsesFirstForwardedHop() throws Exception {
    mockMvc("10.0.0.1, 10.0.0.2")
        .perform(callbackRequest().header("X-Forwarded-For", "10.0.0.2, 1.1.1.1"))
        .andExpect(status().isOk());

    verify(paymentApplicationService).processCallback(any());
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
    PaymentController controller =
        new PaymentController(paymentApplicationService, paymentAssembler);
    // @Value 字段在 standalone 装配下不会被注入，显式设值（与三个 schedule Job 的测试同一手法）
    ReflectionTestUtils.setField(controller, "allowedSourceIpsRaw", allowedSourceIpsRaw);
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
