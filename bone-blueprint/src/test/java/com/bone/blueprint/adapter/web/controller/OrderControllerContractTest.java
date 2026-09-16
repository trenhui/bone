package com.bone.blueprint.adapter.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.handler.CreateOrderCommandHandler;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.handler.OrderPageQueryHandler;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.exception.GlobalExceptionHandler;
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
 *   <li>创建语义必须是 {@code 201 + Location}（API §2.4/§3.2），不能退化成 200；
 *   <li>入参校验失败必须 400，而不是被判成服务端 500；
 *   <li>业务异常必须按<strong>自身携带的 HTTP 状态</strong>翻译（{@code BizException(404, ...)} → 404）， 而不是因为用了默认
 *       500 的错误构造器把「查不到」报成「服务端故障」。
 * </ol>
 *
 * <p>已知偏差（不在此断言、待框架侧修）：{@code ApiResponse.success(data)} 固定 {@code code=200}，与 API §3.1「code 等于
 * HTTP 状态码」在 201 场景下冲突；服务端需要 {@code ApiResponse.success(int code, T data)} 之类的载码工厂。
 */
class OrderControllerContractTest {

  private final CreateOrderCommandHandler createOrderCommandHandler =
      mock(CreateOrderCommandHandler.class);
  private final OrderApplicationService orderApplicationService =
      mock(OrderApplicationService.class);
  private final OrderDetailQueryHandler orderDetailQueryHandler =
      mock(OrderDetailQueryHandler.class);
  private final OrderPageQueryHandler orderPageQueryHandler = mock(OrderPageQueryHandler.class);
  private final OrderAssembler orderAssembler = mock(OrderAssembler.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    OrderController controller =
        new OrderController(
            createOrderCommandHandler,
            orderApplicationService,
            orderDetailQueryHandler,
            orderPageQueryHandler,
            orderAssembler);
    // 注册全局异常处理器 → 业务异常 → HTTP 状态的翻译也在契约范围内被验证
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createReturns201WithLocationAndEnvelope() throws Exception {
    when(orderAssembler.toCreateOrderCommand(any())).thenReturn(mock(CreateOrderCommand.class));
    when(createOrderCommandHandler.handle(any())).thenReturn(42L);

    String body =
        """
        {"customerId": 1, "items": [{"productId": 7, "productName": "样例商品",
         "quantity": 2, "unitPrice": 10.00}]}
        """;

    mockMvc
        .perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/orders/42"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42));
  }

  /**
   * 校验失败：信封必须是 {@code success=false, code=400}。
   *
   * <p><b>待修项（框架侧，非本模块）</b>：{@code GlobalExceptionHandler} 的校验类处理器返回裸 {@code ApiResponse} 而非 {@code
   * ResponseEntity}，HTTP 状态因此仍是 <strong>200</strong>，与 API 规范 §2.4「禁止 HTTP 2xx 且 {@code success:
   * false}」冲突——同一类的 {@code bizExceptionHandler} 已用 {@code ResponseEntity} 给出正确示范。修好前
   * 本用例只断言信封，改好后应把下面一行换成 {@code status().isBadRequest()}。
   */
  @Test
  void createWithInvalidBodyReturns400Envelope() throws Exception {
    // 缺 customerId 与 items → 违背 @NotNull / @NotEmpty
    mockMvc
        .perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": null, \"items\": []}"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  void getDetailWhenMissingReturns404WithStableErrorCode() throws Exception {
    when(orderDetailQueryHandler.handle(any()))
        .thenThrow(new BizException(404, BlueprintErrorCodes.ORDER_NOT_FOUND + ": 999"));

    mockMvc
        .perform(get("/api/v1/orders/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(404))
        // 稳定错误码必须出现在响应里，前端/监控才能按码聚合（错误码登记 §2）
        .andExpect(
            jsonPath("$.message").value(containsString(BlueprintErrorCodes.ORDER_NOT_FOUND)));
  }
}
