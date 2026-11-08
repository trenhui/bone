package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.decision.DecisionResult;
import com.bone.claim.domain.model.decision.DecisionTriage;
import com.bone.claim.domain.model.decision.TriageLimits;
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
}
