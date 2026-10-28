package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AutoApprovalApplicationService.AutoApprovalOutcome;
import com.bone.claim.application.DecisionGraphExecutor.DecisionExecutionResult;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 决策执行用例测试：只有 AUTO_APPROVE 才触发结论消费，其余结论一律不碰资金编排。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DecisionRunApplicationServiceTest {

  private static final long CLAIM = 11L;
  private static final DecisionVersions VERSIONS =
      new DecisionVersions("g1", "r1", "m1", "p1", "c1");

  @Mock private DecisionGraphExecutor decisionGraphExecutor;
  @Mock private AutoApprovalApplicationService autoApprovalApplicationService;

  private DecisionRunApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new DecisionRunApplicationService(decisionGraphExecutor, autoApprovalApplicationService);
  }

  @Test
  @DisplayName("MANUAL_REQUIRED → 不消费，原因 NOT_AUTO_APPROVE")
  void doesNotConsumeManualDecision() {
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("NOT_AUTO_APPROVE");
    verify(autoApprovalApplicationService, never()).applyAutoDecision(anyLong());
  }

  @Test
  @DisplayName("AUTO_APPROVE → 消费，透传受控原因（开关关闭时为 AUTO_APPROVE_DISABLED）")
  void consumesAutoApprove() {
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenReturn(AutoApprovalOutcome.skipped("AUTO_APPROVE_DISABLED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("AUTO_APPROVE_DISABLED");
    verify(autoApprovalApplicationService).applyAutoDecision(CLAIM);
  }

  @Test
  @DisplayName("AUTO_APPROVE 且消费成功 → APPLIED（执行结果原样保留，不被资金编排改写）")
  void reportsApplied() {
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenReturn(new AutoApprovalOutcome(true, "APPLIED", "SO11-1", new BigDecimal("300.00")));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("APPLIED");
    assertThat(run.execution().decisionResult()).isEqualTo("AUTO_APPROVE");
    assertThat(run.execution().traceId()).isEqualTo("t-1");
  }

  @Test
  @DisplayName("消费抛异常不吞（资金失败必须让调用方看到，而不是回一个成功执行结果）")
  void propagatesConsumptionFailure() {
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenThrow(new IllegalStateException("quota down"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.execute(CLAIM, VERSIONS))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("quota down");
  }

  private static DecisionExecutionResult result(String decisionResult) {
    return new DecisionExecutionResult("t-1", CLAIM, decisionResult, "AUTO", "COMPLETED", 5);
  }
}
