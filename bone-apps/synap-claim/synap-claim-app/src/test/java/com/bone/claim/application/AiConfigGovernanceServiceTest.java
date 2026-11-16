package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.decision.TriageLimits;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 批 2 配置治理受控写的表征测试（TDD 钉子）。
 *
 * <p><b>为什么必须相对「当前生效值」而不是相对代码基线</b>：现有 {@code TriageLimits.apply()} 只把配置收敛到代码基线。 若当前库里已收紧到 8000，请求
 * 9000 仍低于代码基线 10000 —— 单看 {@code apply()} 会放行，但相对当前值这是
 * <b>放宽</b>，等于绕过棘轮。本类用这条互补变异钉死「逐字段比较当前生效值」的契约。
 */
class AiConfigGovernanceServiceTest {

  private static final long TENANT = 1L;
  private static final String OPERATOR = "42";
  private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

  private final FeatureToggleRepository repo = mock(FeatureToggleRepository.class);
  private final FeatureTenantToggleService toggleService = mock(FeatureTenantToggleService.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final AiConfigGovernanceService service =
      new AiConfigGovernanceService(repo, toggleService, mapper);

  private static FeatureToggle limitsToggle(String configJson) {
    return FeatureToggle.create(
        TENANT,
        FeatureToggle.FEATURE_TRIAGE_LIMITS,
        FeatureToggle.STATE_ENABLED,
        100,
        "test",
        configJson,
        NOW);
  }

  private static FeatureToggle writeToggle(boolean on) {
    return FeatureToggle.create(
        TENANT,
        AiConfigGovernanceService.FEATURE_CONFIG_WRITE,
        on ? FeatureToggle.STATE_ENABLED : FeatureToggle.STATE_DISABLED,
        100,
        "test",
        null,
        NOW);
  }

  private void stubEffective(String configJson) {
    Map<String, Object> parsed;
    try {
      parsed =
          mapper.readValue(configJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    when(repo.findByFeatureKey(TENANT, FeatureToggle.FEATURE_TRIAGE_LIMITS))
        .thenReturn(limitsToggle(configJson));
    when(repo.findByFeatureKey(TENANT, AiConfigGovernanceService.FEATURE_CONFIG_WRITE))
        .thenReturn(writeToggle(true));
    when(toggleService.triageLimits(TENANT)).thenReturn(TriageLimits.apply(parsed));
  }

  private static Map<String, Object> req(String key, Object value) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put(key, value);
    return m;
  }

  @Test
  @DisplayName("放宽金额上限：9000 高于当前生效值 8000（虽低于代码基线 10000）仍须拒绝")
  void looseningRelativeToCurrentRejected() {
    stubEffective("{\"autoAmountLimit\":8000}");
    assertThatThrownBy(
            () -> service.updateTriageLimits(TENANT, OPERATOR, req("autoAmountLimit", 9000)))
        .hasMessageContaining(ClaimErrorCodes.SC_CLAIM_CONFIG_RATCHET_VIOLATION);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("收紧金额上限：6000 低于当前 8000 须成功，且落库新值与审计备注")
  void tighteningAccepted() throws Exception {
    stubEffective("{\"autoAmountLimit\":8000}");
    service.updateTriageLimits(TENANT, OPERATOR, req("autoAmountLimit", 6000));
    ArgumentCaptor<FeatureToggle> captor = ArgumentCaptor.forClass(FeatureToggle.class);
    verify(repo).save(captor.capture());
    Map<String, Object> written =
        mapper.readValue(
            captor.getValue().getConfigJson(),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});
    assertThat(written).containsEntry("autoAmountLimit", 6000);
    assertThat(captor.getValue().getRemark()).contains("operator=42");
  }

  @Test
  @DisplayName("相等值提交：8000 等于当前生效值，允许幂等写入")
  void equalAccepted() {
    stubEffective("{\"autoAmountLimit\":8000}");
    service.updateTriageLimits(TENANT, OPERATOR, req("autoAmountLimit", 8000));
    verify(repo).save(any());
  }

  @Test
  @DisplayName("置信度门槛方向相反：0.80 高于当前 0.70 是收紧，须成功")
  void confidenceTighteningAccepted() {
    stubEffective("{\"aiManualThreshold\":0.70}");
    service.updateTriageLimits(TENANT, OPERATOR, req("aiManualThreshold", 0.80));
    verify(repo).save(any());
  }

  @Test
  @DisplayName("置信度门槛放宽：0.60 低于当前 0.70 须拒绝")
  void confidenceLooseningRejected() {
    stubEffective("{\"aiManualThreshold\":0.70}");
    assertThatThrownBy(
            () -> service.updateTriageLimits(TENANT, OPERATOR, req("aiManualThreshold", 0.60)))
        .hasMessageContaining(ClaimErrorCodes.SC_CLAIM_CONFIG_RATCHET_VIOLATION);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("写 API 开关关闭：任何配置写请求被拒且库不变（fail-closed）")
  void writeDisabledRejectsAll() {
    stubEffective("{\"autoAmountLimit\":8000}");
    when(repo.findByFeatureKey(TENANT, AiConfigGovernanceService.FEATURE_CONFIG_WRITE))
        .thenReturn(writeToggle(false));
    assertThatThrownBy(
            () -> service.updateTriageLimits(TENANT, OPERATOR, req("autoAmountLimit", 6000)))
        .hasMessageContaining(ClaimErrorCodes.SC_CLAIM_CONFIG_WRITE_DISABLED);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("写 API 开关无记录：fail-closed 视为关闭，不得默认可写")
  void missingWriteToggleFailsClosed() {
    stubEffective("{\"autoAmountLimit\":8000}");
    when(repo.findByFeatureKey(TENANT, AiConfigGovernanceService.FEATURE_CONFIG_WRITE))
        .thenReturn(null);
    assertThatThrownBy(
            () -> service.updateTriageLimits(TENANT, OPERATOR, req("autoAmountLimit", 6000)))
        .hasMessageContaining(ClaimErrorCodes.SC_CLAIM_CONFIG_WRITE_DISABLED);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("未知键：不得静默写入（配了不生效），须返回可辨认错误码")
  void unknownKeyRejected() {
    stubEffective("{\"autoAmountLimit\":8000}");
    assertThatThrownBy(
            () -> service.updateTriageLimits(TENANT, OPERATOR, req("autoAmountLmit", 6000)))
        .hasMessageContaining(ClaimErrorCodes.SC_CLAIM_CONFIG_INVALID);
    verify(repo, never()).save(any());
  }
}
