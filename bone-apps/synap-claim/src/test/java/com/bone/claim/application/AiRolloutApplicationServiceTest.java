package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.config.AiRolloutPolicy;
import com.bone.claim.domain.model.config.AiRolloutStage;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import com.bone.claim.domain.repository.InspectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 放量准入与预算熔断测试。
 *
 * <p>重点在「拒绝的三种原因必须可区分」——{@code AI_STAGE_SHADOW}（该配阶段）、{@code AI_CANARY_MISS}（分桶未命中， 属正常）、{@code
 * AI_BUDGET_EXCEEDED_AUTO_ROLLBACK}（质量退化，该看抽检）对应的处置动作完全不同，混成一个 「不能自动放款」等于把运维引向随机方向。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRolloutApplicationServiceTest {

  private static final long TENANT = 7L;
  private static final long CLAIM = 11L;

  @Mock private FeatureToggleRepository featureToggleRepository;
  @Mock private InspectionRepository inspectionRepository;
  @Mock private DecisionExecutionRepository decisionExecutionRepository;

  private AiRolloutApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new AiRolloutApplicationService(
            featureToggleRepository,
            inspectionRepository,
            decisionExecutionRepository,
            () -> TENANT,
            new ObjectMapper());
  }

  @Test
  @DisplayName("未配置开关 → AI_STAGE_SHADOW（新租户默认不放量，无需任何额外判断）")
  void unconfiguredIsShadow() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any())).thenReturn(null);

    AiRolloutApplicationService.Admission admission = service.admit(TENANT, CLAIM);

    assertThat(admission.allowed()).isFalse();
    assertThat(admission.reason()).isEqualTo("AI_STAGE_SHADOW");
    assertThat(admission.stage()).isEqualTo(AiRolloutStage.SHADOW);
    // 阶段闸先于取数：SHADOW 下不应白跑两次有界查询。
    verify(inspectionRepository, never()).findConcludedInWindow(any(), any());
  }

  @Test
  @DisplayName("开关置 DISABLED 即刻生效（kill switch）：即使配置写着 AUTOMATED 也一律 SHADOW")
  void disabledToggleIsKillSwitch() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("DISABLED", 100, "{\"stage\":\"AUTOMATED\"}"));

    AiRolloutApplicationService.Admission admission = service.admit(TENANT, CLAIM);

    assertThat(admission.allowed()).isFalse();
    assertThat(admission.reason()).isEqualTo("AI_STAGE_SHADOW");
  }

  @Test
  @DisplayName("CANARY 分桶结论与领域策略逐案一致（服务层不得另算一套分桶）")
  void canaryBucketingAgreesWithPolicy() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 50, "{\"stage\":\"CANARY\"}"));
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(List.of());
    AiRolloutPolicy expected =
        new AiRolloutPolicy(AiRolloutStage.CANARY, 50, new BigDecimal("0.02"), 30, 7);

    int misses = 0;
    for (long claimId = 1; claimId <= 20; claimId++) {
      AiRolloutApplicationService.Admission admission = service.admit(TENANT, claimId);
      assertThat(admission.allowed()).isEqualTo(expected.admitsCase(TENANT, claimId));
      if (!admission.allowed()) {
        misses++;
        assertThat(admission.reason()).isEqualTo("AI_CANARY_MISS");
      }
    }
    // 若 20 个案件一个都没落空，说明分桶退化成恒放行，本用例失去判别力——直接判红。
    assertThat(misses).isPositive();
  }

  @Test
  @DisplayName("AUTOMATED 且暂无抽检样本 → 放行（冷启动必须能走通），errorRate 为 null")
  void automatedWithoutEvidenceAdmits() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 100, "{\"stage\":\"AUTOMATED\"}"));
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(List.of());

    AiRolloutApplicationService.Admission admission = service.admit(TENANT, CLAIM);

    assertThat(admission.allowed()).isTrue();
    assertThat(admission.reason()).isEqualTo("ADMITTED");
    assertThat(admission.errorRate()).isNull();
    assertThat(admission.verdict()).isEqualTo("NO_EVIDENCE");
  }

  @Test
  @DisplayName("样本达标且错误率超目标 → 拒绝 + 自动降一档 + 回写现场（AUTOMATED → CANARY）")
  void budgetBreachRollsBackOneStage() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 100, "{\"stage\":\"AUTOMATED\"}"));
    List<DecisionExecution> executions = List.of(autoApproved(CLAIM));
    List<InspectionRecord> concluded = inspections(31, 1);
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(executions);
    when(inspectionRepository.findConcludedInWindow(any(), any())).thenReturn(concluded);

    AiRolloutApplicationService.Admission admission = service.admit(TENANT, CLAIM);

    assertThat(admission.allowed()).isFalse();
    assertThat(admission.reason()).isEqualTo("AI_BUDGET_EXCEEDED_AUTO_ROLLBACK");
    assertThat(admission.autoRolledBack()).isTrue();
    assertThat(admission.stage()).isEqualTo(AiRolloutStage.CANARY);
    assertThat(admission.errorRate()).isEqualByComparingTo("0.0323");

    ArgumentCaptor<FeatureToggle> captor = ArgumentCaptor.forClass(FeatureToggle.class);
    verify(featureToggleRepository).save(captor.capture());
    FeatureToggle saved = captor.getValue();
    assertThat(saved.getConfigJson()).contains("\"stage\":\"CANARY\"");
    assertThat(saved.getConfigJson()).contains("lastRollback");
    assertThat(saved.getConfigJson()).contains("rollbackCount");
    assertThat(saved.getRemark()).contains("自动降档");
  }

  @Test
  @DisplayName("样本不足（1/1 差异）→ 不熔断也不写回滚，只登记 INSUFFICIENT_SAMPLES")
  void insufficientSampleDoesNotRollback() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 100, "{\"stage\":\"AUTOMATED\"}"));
    List<DecisionExecution> executions = List.of(autoApproved(CLAIM));
    List<InspectionRecord> concluded = inspections(1, 1);
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(executions);
    when(inspectionRepository.findConcludedInWindow(any(), any())).thenReturn(concluded);

    AiRolloutApplicationService.Admission admission = service.admit(TENANT, CLAIM);

    assertThat(admission.allowed()).isTrue();
    assertThat(admission.verdict()).isEqualTo("INSUFFICIENT_SAMPLES");
    verify(featureToggleRepository, never()).save(any());
  }

  @Test
  @DisplayName("抽检单不属于自动通过案件 → 不计入分母（否则人工案子的差错会算到 AI 头上）")
  void manualCaseInspectionsAreExcluded() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 100, "{\"stage\":\"AUTOMATED\"}"));
    List<DecisionExecution> executions = List.of(autoApproved(999L));
    List<InspectionRecord> concluded = inspections(31, 1);
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(executions);
    when(inspectionRepository.findConcludedInWindow(any(), any())).thenReturn(concluded);

    AiRolloutApplicationService.Admission admission = service.admit(TENANT, CLAIM);

    assertThat(admission.allowed()).isTrue();
    assertThat(admission.verdict()).isEqualTo("NO_EVIDENCE");
  }

  @Test
  @DisplayName("快照：空集给 null 而非 0，且晋升被 NO_EVIDENCE 阻塞")
  void snapshotEmptyWindow() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 50, "{\"stage\":\"CANARY\"}"));
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(List.of());
    when(inspectionRepository.findConcludedInWindow(any(), any())).thenReturn(List.of());

    AiRolloutApplicationService.RolloutSnapshot snapshot =
        service.snapshot(
            TENANT, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-05T00:00:00Z"));

    assertThat(snapshot.stage()).isEqualTo(AiRolloutStage.CANARY);
    assertThat(snapshot.errorRate()).isNull();
    assertThat(snapshot.remainingRatio()).isNull();
    assertThat(snapshot.inspectedCount()).isZero();
    assertThat(snapshot.promotionReady()).isFalse();
    assertThat(snapshot.promotionBlockers()).containsExactly("NO_EVIDENCE");
    assertThat(snapshot.nextStage()).isEqualTo(AiRolloutStage.AUTOMATED);
  }

  @Test
  @DisplayName("快照只读：即便错误率超目标也不得触发降档（查询不得有副作用）")
  void snapshotHasNoSideEffect() {
    when(featureToggleRepository.findByFeatureKey(anyLong(), any()))
        .thenReturn(toggle("ENABLED", 100, "{\"stage\":\"AUTOMATED\"}"));
    List<DecisionExecution> executions = List.of(autoApproved(CLAIM));
    List<InspectionRecord> concluded = inspections(31, 1);
    when(decisionExecutionRepository.findConvergedInWindow(any(), any())).thenReturn(executions);
    when(inspectionRepository.findConcludedInWindow(any(), any())).thenReturn(concluded);

    AiRolloutApplicationService.RolloutSnapshot snapshot =
        service.snapshot(
            TENANT, Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-05T00:00:00Z"));

    assertThat(snapshot.verdict()).isEqualTo("OVER_TARGET");
    assertThat(snapshot.stage()).isEqualTo(AiRolloutStage.AUTOMATED);
    assertThat(snapshot.promotionBlockers()).containsExactly("ALREADY_AUTOMATED");
    verify(featureToggleRepository, never()).save(any());
  }

  @Test
  @DisplayName("窗口非法（倒置 / 超过 31 天）→ 抛受控错误，不静默裁剪")
  void invalidWindowRejected() {
    Instant from = Instant.parse("2026-11-05T00:00:00Z");
    assertThatThrownBy(() -> service.snapshot(TENANT, from, from))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.snapshot(TENANT, from.minusSeconds(32L * 24 * 3600), from))
        .isInstanceOf(RuntimeException.class);
  }

  // ===================== 夹具 =====================

  private static FeatureToggle toggle(String state, int pct, String configJson) {
    return FeatureToggle.create(
        TENANT, FeatureToggle.FEATURE_AUTO_APPROVE, state, pct, "", configJson, Instant.now());
  }

  private static DecisionExecution autoApproved(long claimId) {
    DecisionExecution execution = mock(DecisionExecution.class);
    when(execution.getClaimId()).thenReturn(claimId);
    when(execution.getDecisionResult()).thenReturn("AUTO_APPROVE");
    return execution;
  }

  /** 生成 {@code total} 条已结论抽检单（同一自动通过案件），其中前 {@code diff} 条为差异。 */
  private static List<InspectionRecord> inspections(int total, int diff) {
    return java.util.stream.IntStream.range(0, total)
        .mapToObj(
            i -> {
              InspectionRecord record = mock(InspectionRecord.class);
              when(record.getClaimId()).thenReturn(CLAIM);
              when(record.getResult()).thenReturn(i < diff ? "DIFF" : "PASS");
              return record;
            })
        .toList();
  }
}
