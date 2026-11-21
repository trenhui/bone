package com.bone.claim.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bone.claim.adapter.web.controller.ClaimIdempotencyProperties;
import com.bone.claim.adapter.web.controller.ReviewTaskController;
import com.bone.claim.adapter.web.controller.ReviewTaskController.BatchDecisionItem;
import com.bone.claim.adapter.web.controller.ReviewTaskController.BatchDecisionReq;
import com.bone.claim.adapter.web.controller.ReviewTaskController.BatchDecisionResp;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.core.exception.BizException;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.idempotency.IdempotencyStore;
import com.bone.core.model.ApiResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/**
 * 批量审核决策（P2-3）判别力测试。
 *
 * <p>用真实 {@link IdempotencyService} + 内存快照而非纯 mock：否则「重放不重复决策」只能验证 mock 被调用， 验证不了稳定子键是否真的命中快照。单条决策走
 * mock，状态冲突由 应用服务抛出，控制器负责隔离到 failed。
 */
@ExtendWith(MockitoExtension.class)
class ReviewTaskBatchDecisionTest {

  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private DecisionExecutionApplicationService decisionExecutionApplicationService;

  private ReviewTaskController controller;

  @BeforeEach
  void setUp() {
    controller =
        new ReviewTaskController(
            claimCaseApplicationService,
            decisionExecutionApplicationService,
            new IdempotencyService(new InMemoryIdempotencyStore()),
            new ClaimIdempotencyProperties());
    MDC.put("userId", "op-77");
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("3 单批量：1 单状态冲突 → 2 成功 1 失败且带 code；重放同幂等键不重复决策成功项")
  void batchDecisionIsPartialSuccessAndIdempotent() {
    // 按 claimId 条件抛错：doThrow(eq(3L),...) 会让 1/2 号调用触发 Mockito 严格桩参数不匹配。
    doAnswer(
            invocation -> {
              if ((long) invocation.getArgument(0) == 3L) {
                throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, "案件状态不允许决策");
              }
              return null;
            })
        .when(claimCaseApplicationService)
        .decide(anyLong(), any(Decision.class), anyString(), any(), any(), any());

    BatchDecisionReq req = new BatchDecisionReq(List.of(item(1), item(2), item(3)), "batch-key-1");

    ApiResponse<BatchDecisionResp> first = controller.batchDecision(req);

    assertThat(first.getData().succeeded()).containsExactly(1L, 2L);
    assertThat(first.getData().failed()).hasSize(1);
    assertThat(first.getData().failed().get(0).claimId()).isEqualTo(3L);
    assertThat(first.getData().failed().get(0).code())
        .isEqualTo(ClaimErrorCodes.CLAIM_STATUS_CONFLICT);
    verify(claimCaseApplicationService, times(1))
        .decide(eq(1L), any(Decision.class), anyString(), any(), any(), any());
    verify(claimCaseApplicationService, times(1))
        .decide(eq(2L), any(Decision.class), anyString(), any(), any(), any());
    verify(claimCaseApplicationService, times(1))
        .decide(eq(3L), any(Decision.class), anyString(), any(), any(), any());

    ApiResponse<BatchDecisionResp> replay = controller.batchDecision(req);

    assertThat(replay.getData().succeeded()).containsExactly(1L, 2L);
    // 成功项命中稳定子键快照 → 不再调用 decide（失败项无快照，允许改状态后重试）
    verify(claimCaseApplicationService, times(1))
        .decide(eq(1L), any(Decision.class), anyString(), any(), any(), any());
    verify(claimCaseApplicationService, times(1))
        .decide(eq(2L), any(Decision.class), anyString(), any(), any(), any());
    verify(claimCaseApplicationService, times(2))
        .decide(eq(3L), any(Decision.class), anyString(), any(), any(), any());
  }

  @Test
  @DisplayName("超过 20 单/批 → SC_CLAIM_TASK_CONFLICT 409，且一单都不执行")
  void overLimitIsRejected() {
    List<BatchDecisionItem> items = IntStream.rangeClosed(1, 21).mapToObj(this::item).toList();
    BatchDecisionReq req = new BatchDecisionReq(items, "batch-key-2");

    assertThatThrownBy(() -> controller.batchDecision(req))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT);
    verifyNoInteractions(claimCaseApplicationService);
  }

  @Test
  @DisplayName("稳定子键只由 批次键+案件ID+动作 决定，不与案件 ID 互相串味")
  void stableKeyIsPerClaim() {
    assertThat(ReviewTaskController.stableClaimActionKey("B1", 7L, "review-decide"))
        .isEqualTo("B1:7:review-decide")
        .isNotEqualTo(ReviewTaskController.stableClaimActionKey("B1", 8L, "review-decide"))
        .isNotEqualTo(ReviewTaskController.stableClaimActionKey("B2", 7L, "review-decide"));
  }

  private BatchDecisionItem item(long claimId) {
    return new BatchDecisionItem(claimId, Decision.APPROVED, null, null);
  }

  /** 内存幂等快照：同一 scopeKey 覆盖写，满足 IdempotencyStore 契约。 */
  private static final class InMemoryIdempotencyStore implements IdempotencyStore {
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
