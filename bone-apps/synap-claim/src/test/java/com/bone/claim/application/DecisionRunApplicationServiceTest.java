package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
  @Mock private DecisionExecutionApplicationService decisionExecutionApplicationService;

  private DecisionRunApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new DecisionRunApplicationService(
            decisionGraphExecutor,
            autoApprovalApplicationService,
            decisionExecutionApplicationService);
  }

  @Test
  @DisplayName("MANUAL_REQUIRED → 不消费，原因 NOT_AUTO_APPROVE")
  void doesNotConsumeManualDecision() {
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("MANUAL_REQUIRED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("NOT_AUTO_APPROVE");
    verify(autoApprovalApplicationService, never()).applyAutoDecision(anyLong());
    // 没有可消费的自动通过结论 ⇒ 不该往执行记录上写消费原因（NULL 才表示"无消费动作"）
    verify(decisionExecutionApplicationService, never())
        .recordConsumption(anyString(), anyString());
  }

  @Test
  @DisplayName("AUTO_APPROVE → 消费，透传受控原因（开关关闭时为 AUTO_APPROVE_DISABLED）并落库")
  void consumesAutoApprove() {
    when(decisionGraphExecutor.execute(CLAIM, VERSIONS)).thenReturn(result("AUTO_APPROVE"));
    when(autoApprovalApplicationService.applyAutoDecision(CLAIM))
        .thenReturn(AutoApprovalOutcome.skipped("AUTO_APPROVE_DISABLED"));

    DecisionRunApplicationService.DecisionRunResult run = service.execute(CLAIM, VERSIONS);

    assertThat(run.consumptionReason()).isEqualTo("AUTO_APPROVE_DISABLED");
    verify(autoApprovalApplicationService).applyAutoDecision(CLAIM);
    // 落库锚点是本次执行的 traceId，不是"案件最新一次执行"（后者可能被并发执行顶掉）
    verify(decisionExecutionApplicationService).recordConsumption("t-1", "AUTO_APPROVE_DISABLED");
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
    verify(decisionExecutionApplicationService).recordConsumption("t-1", "APPLIED");
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

  @Test
  @DisplayName("消费原因落库失败不升级为执行失败（资金动作已成功，不能因诊断信息让客户端重试）")
  void toleratesConsumptionPersistFailure() {
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

  private static DecisionExecutionResult result(String decisionResult) {
    return new DecisionExecutionResult("t-1", CLAIM, decisionResult, "AUTO", "COMPLETED", 5);
  }
}
