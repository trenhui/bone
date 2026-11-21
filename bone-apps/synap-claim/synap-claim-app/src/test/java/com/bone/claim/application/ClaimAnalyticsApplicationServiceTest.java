package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.claim.projection.CaseDurationProjection;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private DomainEventOutboxRepository domainEventOutboxRepository;

  private ClaimAnalyticsApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new ClaimAnalyticsApplicationService(
            decisionExecutionRepository, claimCaseRepository, domainEventOutboxRepository);
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
    assertThat(summary.caseDurationP50Ms()).isNull();
    assertThat(summary.caseDurationP90Ms()).isNull();
  }

  @Test
  @DisplayName("100 案 10 条极端时长：P50/P90 稳定而均值被拉高；复核率与推送成功率分子分母正确")
  void percentilesResistOutliersAndRatesUseCorrectNumeratorDenominator() {
    List<Long> durations = skewedDurations();
    List<DecisionExecution> decisions =
        durations.stream().map(d -> execution("AUTO_APPROVE", "SUCCESS", d.intValue())).toList();
    when(decisionExecutionRepository.findConvergedInWindow(FROM, TO)).thenReturn(decisions);
    List<CaseDurationProjection> durationRows =
        durations.stream().map(d -> new CaseDurationProjection(0L, d)).toList();
    when(claimCaseRepository.caseDurationsInWindow(any(Timestamp.class), any(Timestamp.class)))
        .thenReturn(durationRows);
    when(claimCaseRepository.countReviewedInWindow(any(Timestamp.class), any(Timestamp.class)))
        .thenReturn(30L);
    when(domainEventOutboxRepository.countByStatusInWindow(
            any(Timestamp.class), any(Timestamp.class), eq("SENT")))
        .thenReturn(80L);
    when(domainEventOutboxRepository.countByStatusInWindow(
            any(Timestamp.class), any(Timestamp.class), eq("FAILED")))
        .thenReturn(10L);
    when(domainEventOutboxRepository.countByStatusInWindow(
            any(Timestamp.class), any(Timestamp.class), eq("DEAD")))
        .thenReturn(5L);

    ClaimAnalyticsApplicationService.AnalyticsSummary summary = service.summary(FROM, TO);

    // 90 个正常样本 1000..1089，10 个 10^9 离群值；ceil(0.5*100)=50 → index49，ceil(0.9*100)=90 → index89
    assertThat(summary.caseDurationP50Ms()).isEqualTo(1049.0);
    assertThat(summary.caseDurationP90Ms()).isEqualTo(1089.0);
    assertThat(summary.avgDurationMs()).isGreaterThan(100_000_000.0);
    // 复核率：分子=第二复核人介入 30，分母=已结案 100
    assertThat(summary.reviewedCount()).isEqualTo(100L);
    assertThat(summary.secondReviewedCount()).isEqualTo(30L);
    // 推送成功率：成功 80，失败 = FAILED 10 + DEAD 5
    assertThat(summary.pushSucceeded()).isEqualTo(80L);
    assertThat(summary.pushFailed()).isEqualTo(15L);
  }

  /** 90 条正常时长 + 10 条 10^9 ms 卡单：均值被拉高，P50/P90 不应被带偏。 */
  private static List<Long> skewedDurations() {
    List<Long> values = new ArrayList<>();
    for (int i = 0; i < 90; i++) {
      values.add(1000L + i);
    }
    for (int i = 0; i < 10; i++) {
      values.add(1_000_000_000L);
    }
    return values;
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
