package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.assembler.ClaimCaseAssembler;
import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq;
import com.bone.claim.application.AutoApprovalApplicationService;
import com.bone.claim.application.AutoApprovalApplicationService.AutoApprovalOutcome;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.application.SettlementApplicationService;
import com.bone.claim.application.SettlementApplicationService.SettlementResult;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.idempotency.IdempotencyStore;
import com.bone.core.model.ApiResponse;
import com.bone.core.tenant.context.TenantContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 决策/自动通过/撤销的 HTTP 幂等契约测试。
 *
 * <p>这三条写路径此前**完全不读** Idempotency-Key：「决策已生效但响应在网络上丢失」时， 客户端重试会二次改判、二次结案建单、二次释放预占。测试用真实 {@link
 * IdempotencyService} + 内存存储验证编排语义，而不是把幂等服务 mock 掉——后者只能证明「调用了幂等服务」， 无法证明「同键不会二次执行」。
 *
 * <p>判别力：删掉任一控制器的 replay 分支，对应的「只执行一次」断言即红； 「不同键各自执行」反向断言则防把幂等误做成「同案永不执行」。
 */
class DecisionWriteIdempotencyTest {

  private static final long CLAIM_ID = 1L;
  private static final String OPERATOR = "reviewer-1";

  private ClaimCaseApplicationService claimService;
  private AutoApprovalApplicationService autoApprovalService;
  private SettlementApplicationService settlementService;
  private IdempotencyService idempotency;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId("1");
    MDC.put("userId", OPERATOR);
    claimService = mock(ClaimCaseApplicationService.class);
    autoApprovalService = mock(AutoApprovalApplicationService.class);
    settlementService = mock(SettlementApplicationService.class);
    idempotency = new IdempotencyService(new InMemoryStore());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    MDC.clear();
  }

  @Test
  @DisplayName("工作台决策：同键重放首次结果，不二次改判")
  void reviewDecisionReplaysSameKey() {
    ReviewTaskController controller =
        new ReviewTaskController(
            claimService,
            mock(DecisionExecutionApplicationService.class),
            idempotency,
            new ClaimIdempotencyProperties());
    ReviewDecisionReq req =
        new ReviewDecisionReq(
            Decision.APPROVED, null, null, "证据齐备", List.of(), null, null, BigDecimal.TEN);
    MockHttpServletRequest http = post("/api/v1/review/tasks/1/decision");

    ApiResponse<Void> first = controller.decide(CLAIM_ID, "decision-key-1", req, http);
    ApiResponse<Void> second = controller.decide(CLAIM_ID, "decision-key-1", req, http);

    verify(claimService, times(1))
        .decide(CLAIM_ID, Decision.APPROVED, OPERATOR, "证据齐备", null, BigDecimal.TEN);
    assertThat(second.getCode()).isEqualTo(first.getCode());
  }

  @Test
  @DisplayName("工作台决策：不同键各自执行（幂等不过度应用）")
  void reviewDecisionWithDifferentKeysExecutesEachTime() {
    ReviewTaskController controller =
        new ReviewTaskController(
            claimService,
            mock(DecisionExecutionApplicationService.class),
            idempotency,
            new ClaimIdempotencyProperties());
    ReviewDecisionReq req =
        new ReviewDecisionReq(
            Decision.APPROVED, null, null, "证据齐备", List.of(), null, null, BigDecimal.TEN);
    MockHttpServletRequest http = post("/api/v1/review/tasks/1/decision");

    controller.decide(CLAIM_ID, "decision-a", req, http);
    controller.decide(CLAIM_ID, "decision-b", req, http);

    verify(claimService, times(2))
        .decide(
            eq(CLAIM_ID),
            eq(Decision.APPROVED),
            eq(OPERATOR),
            eq("证据齐备"),
            isNull(),
            eq(BigDecimal.TEN));
  }

  @Test
  @DisplayName("自动通过：同键重放，不二次结案建单")
  void autoApproveReplaysSameKey() {
    ClaimCaseController controller =
        new ClaimCaseController(
            claimService,
            autoApprovalService,
            mock(ClaimCaseAssembler.class),
            idempotency,
            new ClaimIdempotencyProperties());
    when(autoApprovalService.approveAndSettle(anyLong(), anyString()))
        .thenReturn(new AutoApprovalOutcome(true, null, "SO-1", BigDecimal.TEN));
    MockHttpServletRequest http = post("/api/v1/claims/1/auto-approve");

    controller.autoApprove(CLAIM_ID, "auto-key-1", http);
    controller.autoApprove(CLAIM_ID, "auto-key-1", http);

    verify(autoApprovalService, times(1)).approveAndSettle(CLAIM_ID, OPERATOR);
  }

  @Test
  @DisplayName("撤销预占：同键重放，不二次释放额度")
  void cancelReplaysSameKey() {
    SettlementController controller =
        new SettlementController(settlementService, idempotency, new ClaimIdempotencyProperties());
    when(settlementService.cancel(
            anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(new SettlementResult("SO-1", CLAIM_ID, BigDecimal.TEN, "RELEASED", 1));
    MockHttpServletRequest http = post("/api/v1/claims/1/settlement/cancel");

    controller.cancel(CLAIM_ID, "cancel-key-1", new SettlementController.CancelReq(false), http);
    controller.cancel(CLAIM_ID, "cancel-key-1", new SettlementController.CancelReq(false), http);

    verify(settlementService, times(1)).cancel(CLAIM_ID, OPERATOR, false);
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
