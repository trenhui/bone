package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.settlement.AccountType;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 自动通过结论消费者测试。
 *
 * <p>重点不在「happy path 能跑」，而在<b>每一个跳过分支都不许碰聚合</b>——自动放款是资金动作，跳过判定若在调用 {@code autoApprove}
 * 之后发生，案件已经被推进到「已结案」，事务里再返回「未放款」就是欠账。故此处逐条 verify 「未调用 autoApprove / 未建单」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoApprovalApplicationServiceTest {

  private static final long CLAIM = 11L;
  private static final BigDecimal AMOUNT = new BigDecimal("300.00");

  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private SettlementApplicationService settlementApplicationService;
  @Mock private IndemnityApplicationService indemnityApplicationService;
  @Mock private DecisionExecutionApplicationService decisionExecutionApplicationService;

  private AutoApprovalApplicationService service;

  /** 结算结果（局部变量在 thenReturn 内构造会触发嵌套 stubbing，统一提前构造）。 */
  private SettlementApplicationService.SettlementResult settlementResult;

  @BeforeEach
  void setUp() {
    service =
        new AutoApprovalApplicationService(
            claimCaseApplicationService,
            settlementApplicationService,
            indemnityApplicationService,
            decisionExecutionApplicationService);
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "maxAmount", new BigDecimal("1000"));
    when(indemnityApplicationService.latest(CLAIM))
        .thenReturn(new IndemnityApplicationService.AdjustmentResult(null, AMOUNT, "h", "s", null));
  }

  @Test
  @DisplayName("开关关闭 → AUTO_APPROVE_DISABLED，且不触碰任何聚合（策略未签核时零资金动作）")
  void disabledBySwitch() {
    ReflectionTestUtils.setField(service, "enabled", false);

    AutoApprovalApplicationService.AutoApprovalOutcome outcome = service.applyAutoDecision(CLAIM);

    assertThat(outcome.applied()).isFalse();
    assertThat(outcome.reason()).isEqualTo("AUTO_APPROVE_DISABLED");
    verify(claimCaseApplicationService, never()).autoApprove(anyLong(), anyString());
    verify(settlementApplicationService, never()).create(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName("非自动通过结论 → NOT_AUTO_APPROVE:<结论>，不结案")
  void skipsWhenDecisionIsManual() {
    DecisionExecution manual = execution("MANUAL_REQUIRED");
    when(decisionExecutionApplicationService.findLatest(CLAIM)).thenReturn(manual);

    AutoApprovalApplicationService.AutoApprovalOutcome outcome = service.applyAutoDecision(CLAIM);

    assertThat(outcome.reason()).isEqualTo("NOT_AUTO_APPROVE:MANUAL_REQUIRED");
    verify(claimCaseApplicationService, never()).autoApprove(anyLong(), anyString());
  }

  @Test
  @DisplayName("无决策执行记录 → NO_DECISION")
  void skipsWithoutDecision() {
    when(decisionExecutionApplicationService.findLatest(CLAIM)).thenReturn(null);
    assertThat(service.applyAutoDecision(CLAIM).reason()).isEqualTo("NO_DECISION");
  }

  @Test
  @DisplayName("案件已结案 → ALREADY_SETTLED（幂等，不重复建单）")
  void skipsWhenAlreadySettled() {
    DecisionExecution auto = execution("AUTO_APPROVE");
    ClaimCase settled = claim(true);
    when(decisionExecutionApplicationService.findLatest(CLAIM)).thenReturn(auto);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(settled);

    assertThat(service.applyAutoDecision(CLAIM).reason()).isEqualTo("ALREADY_SETTLED");
    verify(settlementApplicationService, never()).create(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName("理算金额超单笔上限 → CAP_EXCEEDED（转人工，不出金）")
  void skipsWhenAmountExceedsCap() {
    DecisionExecution auto = execution("AUTO_APPROVE");
    ClaimCase pending = claim(false);
    when(decisionExecutionApplicationService.findLatest(CLAIM)).thenReturn(auto);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(pending);
    when(indemnityApplicationService.latest(CLAIM))
        .thenReturn(
            new IndemnityApplicationService.AdjustmentResult(
                null, new BigDecimal("1000.01"), "h", "s", null));

    assertThat(service.applyAutoDecision(CLAIM).reason()).isEqualTo("CAP_EXCEEDED");
    verify(claimCaseApplicationService, never()).autoApprove(anyLong(), anyString());
  }

  @Test
  @DisplayName("上限边界含等于（1000 元整仍自动放款）")
  void capIsInclusive() {
    DecisionExecution auto = execution("AUTO_APPROVE");
    ClaimCase pending = claim(false);
    settlementResult = settlement();
    when(decisionExecutionApplicationService.findLatest(CLAIM)).thenReturn(auto);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(pending);
    when(indemnityApplicationService.latest(CLAIM))
        .thenReturn(
            new IndemnityApplicationService.AdjustmentResult(
                null, new BigDecimal("1000.00"), "h", "s", null));
    when(settlementApplicationService.create(anyLong(), anyInt(), any(), anyString()))
        .thenReturn(settlementResult);

    assertThat(service.applyAutoDecision(CLAIM).applied()).isTrue();
  }

  @Test
  @DisplayName("全通过 → 结案 + 建结算单（资金链有单，不再是「结案即完事」）")
  void appliesWhenAllGatesPass() {
    DecisionExecution auto = execution("AUTO_APPROVE");
    ClaimCase pending = claim(false);
    settlementResult = settlement();
    when(decisionExecutionApplicationService.findLatest(CLAIM)).thenReturn(auto);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(pending);
    when(settlementApplicationService.create(anyLong(), anyInt(), any(), anyString()))
        .thenReturn(settlementResult);

    AutoApprovalApplicationService.AutoApprovalOutcome outcome = service.applyAutoDecision(CLAIM);

    assertThat(outcome.applied()).isTrue();
    assertThat(outcome.reason()).isEqualTo("APPLIED");
    assertThat(outcome.settlementNo()).isEqualTo("SO" + CLAIM + "-1");
    verify(claimCaseApplicationService)
        .autoApprove(CLAIM, AutoApprovalApplicationService.SYSTEM_OPERATOR);
    verify(settlementApplicationService)
        .create(CLAIM, 1, AccountType.NO_ACCOUNT, AutoApprovalApplicationService.SYSTEM_OPERATOR);
  }

  @Test
  @DisplayName("人工快捷赔付：不受开关与上限约束，但仍必须建结算单")
  void manualApproveAlwaysSettles() {
    ReflectionTestUtils.setField(service, "enabled", false);
    ClaimCase pending = claim(false);
    settlementResult = settlement();
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(pending);
    when(settlementApplicationService.create(anyLong(), anyInt(), any(), anyString()))
        .thenReturn(settlementResult);

    AutoApprovalApplicationService.AutoApprovalOutcome outcome =
        service.approveAndSettle(CLAIM, "u-1001");

    assertThat(outcome.applied()).isTrue();
    verify(claimCaseApplicationService).autoApprove(CLAIM, "u-1001");
    verify(settlementApplicationService).create(CLAIM, 1, AccountType.NO_ACCOUNT, "u-1001");
  }

  private static DecisionExecution execution(String result) {
    DecisionExecution execution = mock(DecisionExecution.class);
    when(execution.getDecisionResult()).thenReturn(result);
    return execution;
  }

  private static ClaimCase claim(boolean finished) {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getId()).thenReturn(CLAIM);
    when(claim.finished()).thenReturn(finished);
    return claim;
  }

  private static SettlementApplicationService.SettlementResult settlement() {
    return new SettlementApplicationService.SettlementResult(
        "SO" + CLAIM + "-1", CLAIM, AMOUNT, "SOFT_RESERVED", 1);
  }
}
