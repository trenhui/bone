package com.bone.blueprint.adapter.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.response.CreateOrderResp;
import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.CreateOrderCommand;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.core.exception.GlobalExceptionHandler;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 订单 Controller 的 HTTP 契约测试（《Bone-测试策略》§4、《Bone-API-规范》§3.2/§4）。
 *
 * <p>用 standalone MockMvc + mock 应用层：<strong>只测协议契约</strong>（状态码、Location、信封、错误翻译），不启动 Spring
 * 上下文、不连库——因此可以在 CI 里每次 PR 都跑。它锁住三件容易退化的事：
 *
 * <ol>
 *   <li>创建语义必须是 {@code 201}（资源 id 见 {@code data.id}，API §2.4/§3.2），不能退化成 200；
 *   <li>入参校验失败必须 400，而不是被判成服务端 500；
 *   <li>业务异常必须按<strong>自身携带的 HTTP 状态</strong>翻译（{@code BizException(404, ...)} → 404）， 而不是因为用了默认
 *       500 的错误构造器把「查不到」报成「服务端故障」。
 * </ol>
 *
 * <p>载码约定：{@code ApiResponse.success(data)} 默认 {@code code=200}；需要非 200 时必须改调 {@code
 * ApiResponse.success(int code, T data)} 显式载码——create 即如此，保证信封 {@code code} 与 HTTP 201 一致。
 */
class OrderControllerContractTest {

  private final OrderApplicationService orderApplicationService =
      mock(OrderApplicationService.class);
  private final OrderAssembler orderAssembler = mock(OrderAssembler.class);
  private final IdempotencyService idempotencyService = mock(IdempotencyService.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    OrderController controller =
        new OrderController(orderApplicationService, orderAssembler, idempotencyService);
    // 注册全局异常处理器 → 业务异常 → HTTP 状态的翻译也在契约范围内被验证
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createReturns201WithEnvelope() throws Exception {
    when(orderAssembler.toCreateOrderCommand(any())).thenReturn(mock(CreateOrderCommand.class));
    when(orderApplicationService.create(any())).thenReturn(42L);

    String body =
        """
        {"customerId": 1, "items": [{"productId": 7, "productName": "样例商品",
         "quantity": 2, "unitPrice": 10.00}]}
        """;

    mockMvc
        .perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        // 信封 code 必须等于 HTTP 状态码（API 规范 §3.1/§3.2）：201 场景下 code 也必须是 201
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.id").value(42));
  }

  /**
   * 校验失败：HTTP <strong>400</strong> + 信封 {@code success=false, code=400}。
   *
   * <p><b>回归防护</b>：框架 {@code GlobalExceptionHandler} 的校验类处理器曾只返回 {@code ApiResponse} 而不设 HTTP
   * 状态，错误会以 HTTP 200 送达（违反 API 规范 §2.4「禁止 HTTP 2xx 且 {@code success: false}」）。因此本用例
   * <strong>同时</strong>断言状态码与信封——只断言其中之一，该缺陷都能重新溜回来。
   */
  @Test
  void createWithInvalidBodyReturns400() throws Exception {
    // 缺 customerId 与 items → 违背 @NotNull / @NotEmpty
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": null, \"items\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(400));
  }

  /**
   * 幂等重放：同一 {@code Idempotency-Key} 的重复提交必须直接返回历史响应，<strong>不再执行用例</strong>。
   *
   * <p>回归防护：若把「重放」写成「先执行再判断」，重复提交会真的创建第二张订单——幂等形同虚设。因此断言 {@code orderApplicationService.create()}
   * 从未被调用。
   */
  @Test
  void createWithIdempotencyKeyReplaysStoredResponseWithoutExecuting() throws Exception {
    when(orderAssembler.toCreateOrderCommand(any())).thenReturn(mock(CreateOrderCommand.class));
    when(idempotencyService.replay(any(), any(), any(), any(), any()))
        .thenReturn(
            Optional.of(
                new IdempotencyService.ReplayedResponse(
                    201,
                    "/api/v1/orders/42",
                    ApiResponse.success(CreateOrderResp.builder().id(42L).build()))));

    String body =
        """
        {"customerId": 1, "items": [{"productId": 7, "productName": "样例商品",
         "quantity": 2, "unitPrice": 10.00}]}
        """;

    mockMvc
        .perform(
            post("/api/v1/orders")
                .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "k-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(42));

    verify(orderApplicationService, never()).create(any());
  }

  @Test
  void getDetailWhenMissingReturns404WithStableErrorCode() throws Exception {
    when(orderApplicationService.getById(anyLong()))
        .thenThrow(BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND, 999));

    mockMvc
        .perform(get("/api/v1/orders/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(404))
        // 稳定错误码必须出现在响应里，前端/监控才能按码聚合（错误码登记 §2）
        .andExpect(jsonPath("$.message").value(containsString(BlueprintErrorCodes.ORDER_NOT_FOUND)))
        // i18n：码必须在 ProblemDetail.errorCode 里，前端才能 i18n.t('errors.BP_ORDER_NOT_FOUND')。
        // 只断言 message 不够——message 是中文 fallback，英文用户看不到翻译。
        .andExpect(jsonPath("$.data.errorCode").value(BlueprintErrorCodes.ORDER_NOT_FOUND));
  }
}
