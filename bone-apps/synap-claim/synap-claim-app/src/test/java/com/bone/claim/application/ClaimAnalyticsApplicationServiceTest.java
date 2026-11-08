package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 运营指标：影子口径不可被写成实际出金率，降级与终态分布可复算。 */
@ExtendWith(MockitoExtension.class)
class ClaimAnalyticsApplicationServiceTest {

  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

  @Mock private DecisionExecutionRepository decisionExecutionRepository;

  private ClaimAnalyticsApplicationService service;

  @BeforeEach
  void setUp() {
    service = new ClaimAnalyticsApplicationService(decisionExecutionRepository);
  }

  @Test
  @DisplayName("聚合终态分布、降级数与影子自动化率")
  void aggregatesConvergedDecisions() {
    // 先建好夹具再 stub：Mockito 严格模式下，在 when(...) 参数内嵌套创建 mock 会触发 UnfinishedStubbing。
    List<DecisionExecution> rows =
        List.of(
            execution("AUTO_APPROVE", "SUCCESS", 100),
            execution("AUTO_APPROVE", "SUCCESS", 200),
            execution("MANUAL_REQUIRED", "FALLBACK", 300));
    when(decisionExecutionRepository.findConvergedInWindow(FROM, TO)).thenReturn(rows);

    ClaimAnalyticsApplicationService.AnalyticsSummary summary = service.summary(FROM, TO);

    assertThat(summary.converged()).isEqualTo(3);
    assertThat(summary.byDecisionResult())
        .containsEntry("AUTO_APPROVE", 2L)
        .containsEntry("MANUAL_REQUIRED", 1L);
    assertThat(summary.fallbackCount()).isEqualTo(1);
    assertThat(summary.shadowAutomationRate()).isEqualTo(2.0 / 3.0);
    assertThat(summary.avgDurationMs()).isEqualTo(200.0);
    assertThat(summary.scopeNote()).contains("不等于实际出金率");
  }

  @Test
  @DisplayName("空窗口返回零值而非除零或 NPE")
  void emptyWindowIsSafe() {
    when(decisionExecutionRepository.findConvergedInWindow(FROM, TO)).thenReturn(List.of());

    ClaimAnalyticsApplicationService.AnalyticsSummary summary = service.summary(FROM, TO);

    assertThat(summary.converged()).isZero();
    assertThat(summary.shadowAutomationRate()).isZero();
    assertThat(summary.avgDurationMs()).isZero();
  }

  @Test
  @DisplayName("非法时间窗一律拒绝，不静默裁剪")
  void invalidWindowIsRejected() {
    assertThatThrownBy(() -> service.summary(TO, FROM)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.summary(null, TO)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.summary(FROM, FROM.plusSeconds(40 * 86400)))
        .isInstanceOf(RuntimeException.class);
  }

  private static DecisionExecution execution(String result, String status, Integer durationMs) {
    DecisionExecution e = mock(DecisionExecution.class);
    when(e.getDecisionResult()).thenReturn(result);
    when(e.getStatus()).thenReturn(status);
    when(e.getDurationMs()).thenReturn(durationMs);
    return e;
  }
}
