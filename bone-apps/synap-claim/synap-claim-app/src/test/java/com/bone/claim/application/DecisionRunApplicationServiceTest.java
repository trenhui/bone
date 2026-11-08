package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AutoApprovalApplicationService.AutoApprovalOutcome;
import com.bone.claim.application.DecisionGraphExecutor.DecisionExecutionResult;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import com.bone.claim.application.IndemnityApplicationService.AdjustmentResult;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 决策执行用例测试：执行完必须消费结论——<b>先让案件离开初审段（11/13 → 12），再按结论路由到落点</b>。
 *
 * <p>为什么这条契约必须有用例钉住：历史上「消费」只覆盖 AUTO_APPROVE 的分支，且该分支要求案件已在 12， 而没有任何代码把案件从 13 推到 12 ⇒
 * 案件永久停在「自动化初审中」：进不了核赔队列（40）、不会结案（50）、 也不会有任何弱标签。修复前实测：16 次决策执行、0 个案件离开 13。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionRunApplicationServiceTest {

  private static final long CLAIM = 11L;
  private static final String SYSTEM = AutoApprovalApplicationService.SYSTEM_OPERATOR;
  private static final DecisionVersions VERSIONS =
      new DecisionVersions("g1", "r1", "m1", "p1", "c1");

  @Mock private DecisionGraphExecutor decisionGraphExecutor;
  @Mock private AutoApprovalApplicationService autoApprovalApplicationService;
  @Mock private DecisionExecutionApplicationService decisionExecutionApplicationService;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private IndemnityApplicationService indemnityApplicationService;
  @Mock private ClaimCase claim;

  private DecisionRunApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new DecisionRunApplicationService(
            decisionGraphExecutor,
            autoApprovalApplicationService,
            decisionExecutionApplicationService,
            claimCaseApplicationService,
            indemnityApplicationService);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(claim.claimAmount()).thenReturn(new BigDecimal("1000.00"));
  }

  // ===================== 初审段收口（11/13 → 12） =====================

  @Test
  @DisplayName("MANUAL_REQUIRED 且案件在自动化初审中 → 推进到已初审再转人工核赔（否则永久停在 13）")
  void manualDecisionAdvancesThenEscalates() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService).completePreExam(CLAIM, null, SYSTEM);
    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.routingReason()).isEqualTo("ROUTED_TO_ADJUST");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.PENDING_ADJUST.name());
    // 非自动通过结论不得触碰资金编排
    verify(autoApprovalApplicationService, never()).applyAutoDecision(anyLong());
  }

  @Test
  @DisplayName("人工初审中（11）同样收口 → 已初审 → 待核赔")
  void manualPreExaminingAlsoAdvances() {
    givenStatus(ClaimStatus.PRE_EXAMINING);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService).completePreExam(CLAIM, null, SYSTEM);
    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.PENDING_ADJUST.name());
  }

  @Test
  @DisplayName("NEED_MORE_EVIDENCE → 已初审 → 补录中（21），不进核赔队列")
  void needMoreEvidenceRoutesToEvidenceSubmitting() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("NEED_MORE_EVIDENCE"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService).completePreExam(CLAIM, null, SYSTEM);
    verify(claimCaseApplicationService).requestEvidence(CLAIM, SYSTEM);
    verify(claimCaseApplicationService, never()).escalate(anyLong(), anyString());
    assertThat(run.routingReason()).isEqualTo("ROUTED_TO_EVIDENCE");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.EVIDENCE_SUBMITTING.name());
  }

  @Test
  @DisplayName("执行失败（decisionResult 为空）→ 仍按 fail-safe 转人工，不得把案件留在初审中")
  void failedExecutionRoutesToManual() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS))
        .thenReturn(new DecisionExecutionResult("t-1", CLAIM, null, null, "FAILED", 3));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService).completePreExam(CLAIM, null, SYSTEM);
    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.routingReason()).isEqualTo("ROUTED_TO_ADJUST");
  }

  // ===================== 幂等 / 越界保护 =====================

  @Test
  @DisplayName("已在已初审（12，人工已收口）→ 不重复 completePreExam，仍按结论路由")
  void alreadyPreExaminedIsNotCompletedTwice() {
    givenStatus(ClaimStatus.PRE_EXAMINED);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService, never()).completePreExam(anyLong(), any(), anyString());
    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.routingReason()).isEqualTo("ROUTED_TO_ADJUST");
  }

  @Test
  @DisplayName("已在待核赔（40，重复执行）→ 不做任何跃迁，落点如实回传")
  void alreadyInAdjustQueueIsUntouched() {
    givenStatus(ClaimStatus.PENDING_ADJUST);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService, never()).completePreExam(anyLong(), any(), anyString());
    verify(claimCaseApplicationService, never()).escalate(anyLong(), anyString());
    verify(claimCaseApplicationService, never()).requestEvidence(anyLong(), anyString());
    assertThat(run.routingReason()).isEqualTo("ALREADY_ROUTED");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.PENDING_ADJUST.name());
  }

  @Test
  @DisplayName("待初审分配（10）→ 不偷偷替调用方开始初审，交回人工（不是死路：仍在待分配队列）")
  void pendingPreExamIsNotSilentlyStarted() {
    givenStatus(ClaimStatus.PENDING_PRE_EXAM);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService, never()).completePreExam(anyLong(), any(), anyString());
    verify(claimCaseApplicationService, never()).escalate(anyLong(), anyString());
    assertThat(run.routingReason()).isEqualTo("CLAIM_NOT_IN_PRE_EXAM");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.PENDING_PRE_EXAM.name());
  }

  // ===================== AUTO_APPROVE 消费 =====================

  @Test
  @DisplayName("AUTO_APPROVE 消费成功 → 结案（50），绝不转人工")
  void autoApproveAppliedIsNotEscalated() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    givenIndemnity("300.00");
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenReturn(new AutoApprovalOutcome(true, "APPLIED", "SO11-1", new BigDecimal("300.00")));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    // 自动通过前必须落核定金额（否则 domain 层「自动通过前必须回填理算核定金额」直接拒绝）
    verify(claimCaseApplicationService).completePreExam(CLAIM, new BigDecimal("300.00"), SYSTEM);
    verify(autoApprovalApplicationService).applyAutoDecision(CLAIM);
    verify(claimCaseApplicationService, never()).escalate(anyLong(), anyString());
    assertThat(run.routingReason()).isEqualTo("APPLIED");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.FINISHED.name());
    assertThat(run.consumptionReason()).isEqualTo("APPLIED");
  }

  @Test
  @DisplayName("AUTO_APPROVE 被开关/上限拦下 → 必须转人工核赔，不能停在已初审")
  void autoApproveSkippedEscalates() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    givenIndemnity("300.00");
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenReturn(AutoApprovalOutcome.skipped("AUTO_APPROVE_TOGGLE_OFF"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.routingReason()).isEqualTo("ESCALATED_AFTER_SKIP");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.PENDING_ADJUST.name());
    // 消费原因如实透传（读侧靠它回答「为什么没自动放款」）
    assertThat(run.consumptionReason()).isEqualTo("AUTO_APPROVE_TOGGLE_OFF");
    verify(decisionExecutionApplicationService).recordConsumption("t-1", "AUTO_APPROVE_TOGGLE_OFF");
  }

  @Test
  @DisplayName("AUTO_APPROVE 但无理算结果 → 不猜金额、不调资金编排，转人工")
  void autoApproveWithoutIndemnityEscalates() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    when(indemnityApplicationService.latest(CLAIM)).thenReturn(null);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(autoApprovalApplicationService, never()).applyAutoDecision(anyLong());
    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.routingReason()).isEqualTo("NO_INDEMNITY");
    assertThat(run.claimStatus()).isEqualTo(ClaimStatus.PENDING_ADJUST.name());
  }

  @Test
  @DisplayName("理算金额超申请金额（不变量破坏）→ 不放行自动通过，转人工并留痕")
  void autoApproveWithIndemnityExceedingClaimEscalates() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    givenIndemnity("1500.00");
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    verify(autoApprovalApplicationService, never()).applyAutoDecision(anyLong());
    verify(claimCaseApplicationService).escalate(CLAIM, SYSTEM);
    assertThat(run.routingReason()).isEqualTo("INDEMNITY_EXCEEDS_CLAIM");
  }

  // ===================== 既有契约（不得回归） =====================

  @Test
  @DisplayName("MANUAL_REQUIRED → 消费原因 NOT_AUTO_APPROVE，且不往执行记录写消费原因")
  void doesNotConsumeManualDecision() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("NOT_AUTO_APPROVE");
    verify(autoApprovalApplicationService, never()).applyAutoDecision(anyLong());
    // 没有可消费的自动通过结论 ⇒ 不该往执行记录上写消费原因（NULL 才表示"无消费动作"）
    verify(decisionExecutionApplicationService, never())
        .recordConsumption(anyString(), anyString());
  }

  @Test
  @DisplayName("AUTO_APPROVE 消费抛异常不吞（资金失败必须让调用方看到）")
  void propagatesConsumptionFailure() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    givenIndemnity("300.00");
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenThrow(new IllegalStateException("quota down"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.execute(CLAIM, VERSIONS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("quota down");
  }

  @Test
  @DisplayName("消费原因落库失败不升级为执行失败（资金动作已成功，不能因诊断信息让客户端重试）")
  void toleratesConsumptionPersistFailure() {
    givenStatus(ClaimStatus.AUTO_PRE_EXAMINING);
    givenIndemnity("300.00");
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenReturn(new AutoApprovalOutcome(true, "APPLIED", "SO11-1", new BigDecimal("300.00")));
    org.mockito.Mockito.doThrow(new IllegalStateException("deadlock"))
        .when(decisionExecutionApplicationService)
        .recordConsumption("t-1", "APPLIED");

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("APPLIED");
    assertThat(run.execution().traceId()).isEqualTo("t-1");
  }

  // ===================== 夹具 =====================

  private void givenStatus(ClaimStatus status) {
    when(claim.status()).thenReturn(status);
  }

  private void givenIndemnity(String total) {
    when(indemnityApplicationService.latest(CLAIM))
        .thenReturn(
            new AdjustmentResult(
                1L,
                new BigDecimal(total),
                "hash",
                "{}",
                java.util.List.of(),
                null,
                null,
                null,
                true));
  }

  private static DecisionExecutionResult result(String decisionResult) {
    return new DecisionExecutionResult("t-1", CLAIM, decisionResult, "AUTO", "COMPLETED", 5);
  }
}
