package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.decision.DecisionResult;
import com.bone.claim.domain.model.decision.DecisionTriage;
import com.bone.claim.domain.model.decision.TriageLimits;
import com.bone.claim.domain.model.inspection.InspectionConfig;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 阈值读取路径单测（{@code FeatureTenantToggleService#triageLimits}）。
 *
 * <p>这条路径的危险不在「能读到值」，而在<b>读不到时会发生什么</b>：配置缺失、被关掉、JSON 损坏、字段越界——
 * 每一种都必须在「回落代码基线」这一侧收敛，且不得抛异常。因为它的调用方是决策图的收尾分诊： 这里抛一个异常，整次决策就变成
 * FAILED，案件停在半途。**配置坏了最多是「没生效」，不能是「决策失败」。**
 */
@ExtendWith(MockitoExtension.class)
class FeatureTenantToggleServiceTest {

  private static final long TENANT = 42L;

  @Mock private FeatureToggleRepository repository;

  private FeatureTenantToggleService service;

  @BeforeEach
  void setUp() {
    service = new FeatureTenantToggleService(repository, new ObjectMapper());
  }

  private FeatureToggle toggle(String state, int rolloutPct, String configJson) {
    return FeatureToggle.create(
        TENANT,
        FeatureToggle.FEATURE_TRIAGE_LIMITS,
        state,
        rolloutPct,
        "",
        configJson,
        Instant.now());
  }

  @Test
  @DisplayName("无记录 ⇒ 代码基线（零配置零变更）")
  void noRecordYieldsDefaults() {
    when(repository.findByFeatureKey(anyLong(), anyString())).thenReturn(null);
    TriageLimits.Resolution r = service.triageLimits(TENANT);
    assertThat(r.limits()).isEqualTo(TriageLimits.defaults());
    assertThat(r.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("显式 DISABLED ⇒ 回落基线（这是运维「回滚到默认」的正规手段，不该报错）")
  void disabledYieldsDefaultsWithoutRejection() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_DISABLED, 100, "{\"autoAmountLimit\":\"5000\"}"));
    TriageLimits.Resolution r = service.triageLimits(TENANT);
    assertThat(r.limits()).isEqualTo(TriageLimits.defaults());
    assertThat(r.rejected()).isEmpty();
  }

  @Test
  @DisplayName("灰度未命中（rollout_pct=0）⇒ 基线")
  void rolloutNotHitYieldsDefaults() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_ENABLED, 0, "{\"autoAmountLimit\":\"5000\"}"));
    assertThat(service.triageLimits(TENANT).limits()).isEqualTo(TriageLimits.defaults());
  }

  @Test
  @DisplayName("开启但 config_json 为空 ⇒ 基线（不是「空配置 = 全 0」）")
  void blankConfigYieldsDefaults() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_ENABLED, 100, null));
    assertThat(service.triageLimits(TENANT).limits()).isEqualTo(TriageLimits.defaults());
  }

  @Test
  @DisplayName("合法配置 ⇒ 生效")
  void validConfigTakesEffect() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(
            toggle(
                FeatureToggle.STATE_ENABLED,
                100,
                "{\"autoAmountLimit\":\"8000\",\"aiManualThreshold\":\"0.75\"}"));
    TriageLimits.Resolution r = service.triageLimits(TENANT);
    assertThat(r.limits().autoAmountLimit()).isEqualByComparingTo("8000");
    assertThat(r.limits().aiManualThreshold()).isEqualByComparingTo("0.75");
    assertThat(r.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("JSON 损坏 ⇒ 不抛异常、回落基线，且拒绝项含 $json（坏配置不能拖挂决策）")
  void brokenJsonFallsBackInsteadOfThrowing() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_ENABLED, 100, "{not-json"));
    TriageLimits.Resolution r = service.triageLimits(TENANT);
    assertThat(r.limits()).isEqualTo(TriageLimits.defaults());
    assertThat(r.rejected()).containsKey(FeatureTenantToggleService.JSON_SENTINEL);
    assertThat(r.rejected().get(FeatureTenantToggleService.JSON_SENTINEL)).contains("回落代码基线");
  }

  @Test
  @DisplayName("JSON 是数组而非对象 ⇒ 回落基线（类型不符也不抛）")
  void nonObjectJsonFallsBack() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_ENABLED, 100, "[1,2,3]"));
    TriageLimits.Resolution r = service.triageLimits(TENANT);
    assertThat(r.limits()).isEqualTo(TriageLimits.defaults());
    assertThat(r.rejected()).isNotEmpty();
  }

  @Test
  @DisplayName("JSON 里的放宽项被拒，其余项仍生效（逐键独立，不整组回滚）")
  void partialRejectionKeepsValidKeys() {
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(
            toggle(
                FeatureToggle.STATE_ENABLED,
                100,
                "{\"autoAmountLimit\":\"50000\",\"ocrManualThreshold\":\"0.9\"}"));
    TriageLimits.Resolution r = service.triageLimits(TENANT);
    assertThat(r.limits().autoAmountLimit()).isEqualByComparingTo("10000");
    assertThat(r.limits().ocrManualThreshold()).isEqualByComparingTo("0.9");
    assertThat(r.rejected()).containsKey(TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT);
  }

  @Test
  @DisplayName("端到端：库里的配置真的改变分诊结论（不是「读到了但没人用」）")
  void configActuallyChangesTriageOutcome() {
    // 9000 元 + 全闸通过：基线（上限 1 万）下自动通过
    DecisionTriage.Input in =
        new DecisionTriage.Input(
            true,
            true,
            false,
            new BigDecimal("9000"),
            new BigDecimal("0.9"),
            new BigDecimal("0.9"),
            false,
            true,
            false);
    when(repository.findByFeatureKey(anyLong(), anyString())).thenReturn(null);
    assertThat(DecisionTriage.evaluate(in, service.triageLimits(TENANT).limits()).result())
        .isEqualTo(DecisionResult.AUTO_APPROVE);

    // 运维把上限收紧到 8000 ⇒ 同一案件落复核档（无需发版）
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_ENABLED, 100, "{\"autoAmountLimit\":\"8000\"}"));
    DecisionTriage.Outcome tightened =
        DecisionTriage.evaluate(in, service.triageLimits(TENANT).limits());
    assertThat(tightened.result()).isEqualTo(DecisionResult.MANUAL_REQUIRED);
    assertThat(tightened.reason()).isEqualTo("AMOUNT_OVER_LIMIT");

    // 放宽尝试（改成 5 万）不生效 ⇒ 结论仍是复核档，与收紧时一致
    when(repository.findByFeatureKey(anyLong(), anyString()))
        .thenReturn(toggle(FeatureToggle.STATE_ENABLED, 100, "{\"autoAmountLimit\":\"50000\"}"));
    assertThat(DecisionTriage.evaluate(in, service.triageLimits(TENANT).limits()).result())
        .isEqualTo(DecisionResult.AUTO_APPROVE);
  }

  // ===== 批 4 · INSPECTION_ACTION fail-closed（4.3） =====

  private FeatureToggle inspectionToggle(String state) {
    return FeatureToggle.create(
        TENANT, FeatureToggle.FEATURE_INSPECTION_ACTION, state, 100, "", null, Instant.now());
  }

  @Test
  @DisplayName("4.3 INSPECTION_ACTION 未配置 ⇒ 关闭（fail-closed，不生成重办任务）")
  void inspectionActionAbsentIsDisabled() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_INSPECTION_ACTION))
        .thenReturn(null);
    assertThat(service.isInspectionActionEnabled(TENANT, true)).isFalse();
  }

  @Test
  @DisplayName("4.3 INSPECTION_ACTION 显式 ENABLED ⇒ 开启")
  void inspectionActionEnabledWhenOn() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_INSPECTION_ACTION))
        .thenReturn(inspectionToggle(FeatureToggle.STATE_ENABLED));
    assertThat(service.isInspectionActionEnabled(TENANT, true)).isTrue();
  }

  @Test
  @DisplayName("4.3 全局总开关关闭 ⇒ 一律关闭（最终裁决）")
  void inspectionActionGlobalOffWins() {
    assertThat(service.isInspectionActionEnabled(TENANT, false)).isFalse();
  }

  // ===== 审核疲劳防控 · 配置消费 fail-closed =====

  private FeatureToggle fatigueToggle(String state, String configJson) {
    return FeatureToggle.create(
        TENANT,
        FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD,
        state,
        100,
        "",
        configJson,
        Instant.now());
  }

  @Test
  @DisplayName("审核疲劳：无记录 / 关闭均回落基线")
  void fatigueAbsentOrDisabledUsesDefaults() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD))
        .thenReturn(null);
    assertThat(service.reviewFatigueConfig(TENANT)).isEqualTo(ReviewFatigueConfig.defaults());

    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD))
        .thenReturn(fatigueToggle(FeatureToggle.STATE_DISABLED, "{\"maxPerShift\":1}"));
    assertThat(service.reviewFatigueConfig(TENANT)).isEqualTo(ReviewFatigueConfig.defaults());
  }

  @Test
  @DisplayName("审核疲劳：合法配置生效")
  void fatigueValidConfigApplies() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD))
        .thenReturn(
            fatigueToggle(
                FeatureToggle.STATE_ENABLED,
                "{\"maxPerShift\":5,\"restPromptMinutes\":90,"
                    + "\"shiftEndExclusionMinutes\":45,\"sensitiveAmountYuan\":20000}"));
    ReviewFatigueConfig cfg = service.reviewFatigueConfig(TENANT);
    assertThat(cfg.maxPerShift()).isEqualTo(5);
    assertThat(cfg.restPromptMinutes()).isEqualTo(90);
    assertThat(cfg.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("审核疲劳：非法 JSON 不抛异常，回落基线且登记 $json")
  void fatigueBrokenJsonFallsBack() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD))
        .thenReturn(fatigueToggle(FeatureToggle.STATE_ENABLED, "{oops"));
    ReviewFatigueConfig cfg = service.reviewFatigueConfig(TENANT);
    assertThat(cfg.maxPerShift()).isEqualTo(ReviewFatigueConfig.DEFAULT_MAX_PER_SHIFT);
    assertThat(cfg.restPromptMinutes()).isEqualTo(ReviewFatigueConfig.DEFAULT_REST_PROMPT_MINUTES);
    assertThat(cfg.rejected()).containsKey(FeatureTenantToggleService.JSON_SENTINEL);
  }

  @Test
  @DisplayName("审核疲劳：越界字段按字段回落并登记，不静默生效")
  void fatigueInvalidFieldRejected() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD))
        .thenReturn(fatigueToggle(FeatureToggle.STATE_ENABLED, "{\"maxPerShift\":0}"));
    ReviewFatigueConfig cfg = service.reviewFatigueConfig(TENANT);
    assertThat(cfg.maxPerShift()).isEqualTo(ReviewFatigueConfig.DEFAULT_MAX_PER_SHIFT);
    assertThat(cfg.rejected()).containsKey("maxPerShift");
  }

  // ===== V1.0 · INSPECTION_SAMPLING 抽样比例配置（fail-safe 回落基线 5%） =====

  private FeatureToggle samplingToggle(String state, String configJson) {
    return FeatureToggle.create(
        TENANT,
        FeatureToggle.FEATURE_INSPECTION_SAMPLING,
        state,
        100,
        "",
        configJson,
        Instant.now());
  }

  @Test
  @DisplayName("抽检比例：无记录 ⇒ 回落 5%（fail-safe，绝不默认 0 不抽或 100 全量）")
  void samplingAbsentFallsBackToFive() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_INSPECTION_SAMPLING))
        .thenReturn(null);
    InspectionConfig cfg = service.inspectionConfig(TENANT);
    assertThat(cfg.samplingRatePercent()).isEqualTo(InspectionConfig.DEFAULT_SAMPLING_RATE);
  }

  @Test
  @DisplayName("抽检比例：显式配置 100 ⇒ 全量生效（V1.0 口径）")
  void samplingHundredTakesEffect() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_INSPECTION_SAMPLING))
        .thenReturn(samplingToggle(FeatureToggle.STATE_ENABLED, "{\"samplingRatePercent\":100}"));
    InspectionConfig cfg = service.inspectionConfig(TENANT);
    assertThat(cfg.samplingRatePercent()).isEqualTo(100);
    assertThat(cfg.fullyApplied()).isTrue();
  }

  @Test
  @DisplayName("抽检比例：非法 JSON ⇒ 不抛异常、回落 5% 且登记 $json（坏配置不拖挂抽样）")
  void samplingBrokenJsonFallsBack() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_INSPECTION_SAMPLING))
        .thenReturn(samplingToggle(FeatureToggle.STATE_ENABLED, "{not-json"));
    InspectionConfig cfg = service.inspectionConfig(TENANT);
    assertThat(cfg.samplingRatePercent()).isEqualTo(InspectionConfig.DEFAULT_SAMPLING_RATE);
    assertThat(cfg.rejected()).containsKey(FeatureTenantToggleService.JSON_SENTINEL);
  }

  @Test
  @DisplayName("抽检比例：仓储读取抛异常 ⇒ 不冒泡、回落 5% 且登记诊断（DB 故障不等于该笔不抽检）")
  void samplingRepositoryFailureFallsBack() {
    when(repository.findByFeatureKey(TENANT, FeatureToggle.FEATURE_INSPECTION_SAMPLING))
        .thenThrow(new RuntimeException("db down"));
    InspectionConfig cfg = service.inspectionConfig(TENANT);
    assertThat(cfg.samplingRatePercent()).isEqualTo(InspectionConfig.DEFAULT_SAMPLING_RATE);
    assertThat(cfg.fullyApplied()).isFalse();
    assertThat(cfg.rejected()).containsKey(FeatureTenantToggleService.JSON_SENTINEL);
    assertThat(cfg.rejected().get(FeatureTenantToggleService.JSON_SENTINEL)).contains("db down");
  }
}
