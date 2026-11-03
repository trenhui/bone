package com.bone.claim.application;

import com.bone.claim.application.port.out.TriageLimitsPort;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.decision.TriageLimits;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 特性开关服务（§11.4 三态开关 + 阈值外置）。
 *
 * <p><b>fail-closed 设计</b>：未给租户配置某特性开关时，一律视为「关闭」——绝不默认放量自动放款等资金策略。 全局总开关（{@code
 * bone.claim.auto-approve.enabled}）仍是最终裁决；本服务只在其开启时做<b>租户级细化</b> （按租户开关 /
 * 灰度比例裁决），从而支持「新能力先小流量灰度、再全量」的渐进发布，而非一刀切。
 *
 * <p><b>阈值外置</b>：{@link #triageLimits(long)} 读 {@code TRIAGE_LIMITS} 的 {@code config_json}，
 * 供分诊按租户调整 R-FAST/R-MAN 阈值（§8.3 / §11.4「改阈值不发版」）。解析放在应用层而非领域层， 是为了让 domain 不依赖 JSON 库——领域值对象 {@link
 * TriageLimits} 只接收已解析的键值对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureTenantToggleService implements TriageLimitsPort {

  /** 非法 JSON 的伪键（便于日志与拒绝摘要里定位到「整段配置坏了」而非某个字段）。 */
  static final String JSON_SENTINEL = "$json";

  private final FeatureToggleRepository featureToggleRepository;
  private final ObjectMapper objectMapper;

  /**
   * 某租户某特性是否允许。
   *
   * @param tenantId 租户（灰度 hash 入参）
   * @param globalEnabled 全局总开关（最终裁决；关闭则一律 false）
   * @param featureKey 特性键（如 {@link FeatureToggle#FEATURE_AUTO_APPROVE}）
   * @return 允许放行则返回 true
   */
  public boolean isFeatureAllowed(long tenantId, boolean globalEnabled, String featureKey) {
    if (!globalEnabled) {
      return false;
    }
    FeatureToggle toggle = featureToggleRepository.findByFeatureKey(tenantId, featureKey);
    if (toggle == null) {
      // 未显式配置：继承全局「开启」，兼容存量行为（全局默认关闭时早已在上一行拦截）
      return true;
    }
    return toggle.isOn(tenantId);
  }

  /**
   * 某租户生效的分诊阈值。
   *
   * <p>读取顺序：无记录 / 未开启 / 无 {@code config_json} ⇒ 代码基线（零行为变更）；有配置 ⇒ 解析后按「只能更严」校验。
   *
   * <p><b>任何异常都不得改变资金口径</b>：JSON 非法、类型不符、字段越界，一律回落基线并 WARN，而不是抛出把决策链拖挂， 也不是静默采信一半。返回的 {@link
   * TriageLimits.Resolution#rejected()} 会说明哪些键没生效、为什么。
   *
   * @param tenantId 租户
   * @return 生效阈值 + 未生效项（永不返回 null）
   */
  @Override
  public TriageLimits.Resolution triageLimits(long tenantId) {
    FeatureToggle toggle =
        featureToggleRepository.findByFeatureKey(tenantId, FeatureToggle.FEATURE_TRIAGE_LIMITS);
    if (toggle == null) {
      return new TriageLimits.Resolution(TriageLimits.defaults(), Map.of());
    }
    if (!toggle.isOn(tenantId)) {
      // 显式 DISABLED 或未命中灰度：按基线跑（这是运维的「回滚到默认」手段，不该报错）
      return new TriageLimits.Resolution(TriageLimits.defaults(), Map.of());
    }
    String raw = toggle.getConfigJson();
    if (raw == null || raw.isBlank()) {
      return new TriageLimits.Resolution(TriageLimits.defaults(), Map.of());
    }
    Map<String, Object> parsed;
    try {
      parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.warn(
          "[阈值外置] config_json 解析失败，回落代码基线（决策不受影响）：tenantId={}, feature={}, err={}",
          tenantId,
          FeatureToggle.FEATURE_TRIAGE_LIMITS,
          e.getMessage());
      Map<String, String> rejected = new LinkedHashMap<>();
      rejected.put(JSON_SENTINEL, "不是合法的 JSON 对象（" + e.getMessage() + "）；已回落代码基线");
      return new TriageLimits.Resolution(TriageLimits.defaults(), rejected);
    }
    if (parsed == null) {
      return new TriageLimits.Resolution(TriageLimits.defaults(), Map.of());
    }
    TriageLimits.Resolution resolution = TriageLimits.apply(parsed);
    if (!resolution.fullyApplied()) {
      log.warn(
          "[阈值外置] 部分配置未生效（其余项已生效）：tenantId={}, rejected={}",
          tenantId,
          resolution.rejectionSummary());
    }
    return resolution;
  }
}
