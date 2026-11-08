package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.SettlementApplicationService;
import com.bone.claim.application.SettlementApplicationService.SettlementResult;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.idempotency.IdempotencyStore;
import com.bone.core.model.ApiResponse;
import com.bone.core.tenant.context.TenantContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 资金出金端点的<b>HTTP 幂等</b>契约测试。
 *
 * <p><b>为什么值得单独测</b>：{@code pay}/{@code refund} 是真正动钱的写接口。领域状态机只能保证「已 PAID 不再扣减」，
 * 但<b>「已成功、响应却在网络上丢了」</b>的客户端重试会吃到一个状态冲突（看起来像失败），调用方无法判断钱到底划没划走。 行业做法（Stripe / 支付网关）是在 HTTP 层用
 * {@code Idempotency-Key} 重放首次成功响应，让重试是幂等的。
 *
 * <p>本测试用真实 {@link IdempotencyService} + 内存 {@link IdempotencyStore} 验证编排语义，而不是把幂等服务 mock 掉——
 * 后者只能证明「调用了幂等服务」，无法证明「同键不二次出金」。
 *
 * <p>判别力（删掉实现里的 replay 分支即红）：
 *
 * <ul>
 *   <li>同键第二次调用必须<b>不</b>再触达 {@code SettlementApplicationService.pay}；
 *   <li>两次响应体必须全等（重放首次结果，而不是二次执行后的新结果）；
 *   <li>反向对照：不同幂等键必须各自执行（证明幂等没有被过度应用成「永不执行」）。
 * </ul>
 */
class SettlementControllerIdempotencyTest {

  private static final long CLAIM_ID = 1L;
  private static final String OPERATOR = "reviewer-1";

  private SettlementApplicationService settlementService;
  private SettlementController controller;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId("1");
    MDC.put("userId", OPERATOR);
    settlementService = mock(SettlementApplicationService.class);
    controller =
        new SettlementController(settlementService, new IdempotencyService(new InMemoryStore()));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    MDC.clear();
  }

  @Test
  @DisplayName("支付幂等：同 Idempotency-Key 重放首次结果，且不二次出金")
  void payReplaysSameKeyWithoutSecondExecution() {
    SettlementResult paid =
        new SettlementResult("SO1-1", CLAIM_ID, new BigDecimal("100.00"), "PAID", 1);
    when(settlementService.pay(anyLong(), anyString())).thenReturn(paid);
    MockHttpServletRequest request = post("/api/v1/claims/1/settlement/pay");

    ApiResponse<SettlementResult> first = controller.pay(CLAIM_ID, "key-pay-1", request);
    ApiResponse<?> second = controller.pay(CLAIM_ID, "key-pay-1", request);

    verify(settlementService, times(1)).pay(CLAIM_ID, OPERATOR);
    // 快照往返后金额变为 JSON 数字（如 100.0），数值语义不变；按字段/数值比较，而非字符串
    SettlementResult original = first.getData();
    Map<?, ?> replayed = (Map<?, ?>) second.getData();
    assertThat(replayed.get("settlementNo")).isEqualTo(original.settlementNo());
    assertThat(replayed.get("fundingStage")).isEqualTo(original.fundingStage());
    assertThat(replayed.get("attempt")).isEqualTo(original.attempt());
    assertThat(new BigDecimal(String.valueOf(replayed.get("amount"))))
        .isEqualByComparingTo(original.amount());
  }

  @Test
  @DisplayName("不同 Idempotency-Key 各自执行（幂等不被过度应用）")
  void payWithDifferentKeysExecutesEachTime() {
    when(settlementService.pay(anyLong(), anyString()))
        .thenReturn(new SettlementResult("SO1-1", CLAIM_ID, new BigDecimal("100.00"), "PAID", 1));
    MockHttpServletRequest request = post("/api/v1/claims/1/settlement/pay");

    controller.pay(CLAIM_ID, "key-a", request);
    controller.pay(CLAIM_ID, "key-b", request);

    verify(settlementService, times(2)).pay(CLAIM_ID, OPERATOR);
  }

  @Test
  @DisplayName("退款幂等：同 Idempotency-Key 重放首次结果，且不重复红冲")
  void refundReplaysSameKeyWithoutSecondExecution() {
    SettlementResult refunded =
        new SettlementResult("SO1-1", CLAIM_ID, new BigDecimal("100.00"), "RELEASED", 1);
    when(settlementService.refund(anyLong(), anyString())).thenReturn(refunded);
    MockHttpServletRequest request = post("/api/v1/claims/1/settlement/refund");

    ApiResponse<SettlementResult> first = controller.refund(CLAIM_ID, "key-refund-1", request);
    ApiResponse<?> second = controller.refund(CLAIM_ID, "key-refund-1", request);

    verify(settlementService, times(1)).refund(CLAIM_ID, OPERATOR);
    SettlementResult original = first.getData();
    Map<?, ?> replayed = (Map<?, ?>) second.getData();
    assertThat(replayed.get("settlementNo")).isEqualTo(original.settlementNo());
    assertThat(replayed.get("fundingStage")).isEqualTo(original.fundingStage());
    assertThat(replayed.get("attempt")).isEqualTo(original.attempt());
    assertThat(new BigDecimal(String.valueOf(replayed.get("amount"))))
        .isEqualByComparingTo(original.amount());
  }

  private static MockHttpServletRequest post(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRequestURI(uri);
    return request;
  }

  /** 内存版幂等存储：仅本测试使用；生产真源仍由 infrastructure 的 IdempotencyStoreAdapter 承担。 */
  private static final class InMemoryStore implements IdempotencyStore {

    private final Map<String, Snapshot> snapshots = new HashMap<>();

    @Override
    public Optional<Snapshot> find(String scopeKey) {
      return Optional.ofNullable(snapshots.get(scopeKey));
    }

    @Override
    public void put(String scopeKey, Snapshot snapshot, Duration ttl) {
      snapshots.put(scopeKey, snapshot);
    }
  }
}
