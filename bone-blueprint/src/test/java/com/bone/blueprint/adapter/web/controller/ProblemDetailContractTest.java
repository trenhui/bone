package com.bone.blueprint.adapter.web.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.assembler.OrderAssemblerImpl;
import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.GlobalExceptionHandler;
import com.bone.core.idempotency.IdempotencyService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 错误响应的 {@code ProblemDetail} 契约测试（i18n 方案 §6.2.4）。
 *
 * <p><b>为什么必须有这一层，而不是只靠 CI 脚本</b>：{@code check-i18n-sync.py} 只能比对「后端码集合 ⊆ 前端语言包 key」，
 * 它<b>查不出「handler 根本没产出 ProblemDetail」</b>——那种情况下响应 {@code data} 为 {@code null}， 前端 {@code
 * showError()} 的 errorCode 分支永远不命中，英文用户只会看到中文 toast， 而码表与语言包完全对得上，脚本一片绿灯。因此"非 2xx 必带
 * errorCode"必须用<b>行为级</b>断言守住。
 *
 * <p><b>本模块是 i18n 改造样板</b>：其余模块（bone-system / masterdata / generator / bone-web 兜底） 统一到 {@code
 * ProblemDetail} 模型时，各自补一条同类测试即可。
 */
class ProblemDetailContractTest {

  private final OrderApplicationService orderApplicationService =
      mock(OrderApplicationService.class);
  private final OrderAssembler orderAssembler = mock(OrderAssembler.class);
  private final IdempotencyService idempotencyService = mock(IdempotencyService.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    OrderController controller =
        new OrderController(orderApplicationService, orderAssembler, idempotencyService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  /**
   * 业务异常：码必须由异常自带并透传进 {@code data.errorCode}。
   *
   * <p>回归防护：旧实现是 {@code ApiResponse.error(code, "中文")}，{@code data} 为 {@code null}， 前端只能把中文 message
   * 原样展示——英文用户看到中文，且没有任何测试会发现。
   */
  @Test
  void bizExceptionCarriesErrorCodeInProblemDetail() throws Exception {
    when(orderApplicationService.getById(anyLong()))
        .thenThrow(BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND, 999));

    mockMvc
        .perform(get("/api/v1/orders/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.data").value(not(nullValue())))
        .andExpect(jsonPath("$.data.errorCode").value(BlueprintErrorCodes.ORDER_NOT_FOUND))
        .andExpect(jsonPath("$.data.status").value(404))
        // title 用 HTTP 状态文本（人读），type 是稳定的 problem URI（机器读），二者不可互换
        .andExpect(jsonPath("$.data.title").value("Not Found"))
        .andExpect(jsonPath("$.data.type").value(not(nullValue())))
        // message 仍是中文 fallback（展示分离契约），不得为空
        .andExpect(jsonPath("$.message").value(not(nullValue())));
  }

  /**
   * 参数校验失败：码统一为 {@code COMMON_VALIDATION_FAILED}，并给出<b>结构化字段错误</b>。
   *
   * <p>结构化 {@code data.errors} 的意义：前端能在英文界面逐字段展示（{@code field} 是稳定的字段名， 可再按字段做 i18n），而不是把一整串中文
   * {@code detail} 塞给用户。
   */
  @Test
  void validationFailureCarriesErrorCodeAndFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": null, \"items\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.data.errorCode").value(CommonErrorCodes.VALIDATION_FAILED))
        .andExpect(jsonPath("$.data.errors").isArray())
        .andExpect(jsonPath("$.data.errors[0].field").value(not(nullValue())))
        .andExpect(jsonPath("$.data.errors[0].message").value(not(nullValue())));
  }

  /**
   * 读模型（{@code LocalDateTime}，UTC 归一）→ 对外契约（{@code Instant}）。
   *
   * <p>为什么单独测这一行：MapStruct 不会凭空猜时区。若删掉 {@link OrderAssembler#toInstant} 里的 {@code ZoneOffset.UTC}
   * 而落到系统默认时区，接口在不同时区的实例上会返回不同的时刻值， 而契约测试里看不到——因为本地跑永远"刚好对"。这里用固定输入把口径钉死（i18n 方案 §6.4）。
   */
  @Test
  void readModelTimeIsConvertedToUtcInstant() {
    // MapStruct 生成的实现类（target/generated-sources），用它来调 assembler 的 default 方法
    OrderAssembler assembler = new OrderAssemblerImpl();
    LocalDateTime readModelTime = LocalDateTime.of(2026, 9, 25, 12, 0, 0);

    assertEquals(Instant.parse("2026-09-25T12:00:00Z"), assembler.toInstant(readModelTime));
    assertEquals(
        Instant.parse("2026-09-25T12:00:00Z"),
        readModelTime.toInstant(ZoneOffset.UTC),
        "归一口径必须与 OrderHeadProjection.from() 一致");
  }
}
