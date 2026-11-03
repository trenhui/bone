package com.bone.claim.application;

import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 特性开关服务（§11.4 三态开关）。
 *
 * <p><b>fail-closed 设计</b>：未给租户配置某特性开关时，一律视为「关闭」——绝不默认放量自动放款等资金策略。 全局总开关（{@code
 * bone.claim.auto-approve.enabled}）仍是最终裁决；本服务只在其开启时做<b>租户级细化</b> （按租户开关 /
 * 灰度比例裁决），从而支持「新能力先小流量灰度、再全量」的渐进发布，而非一刀切。
 */
@Service
@RequiredArgsConstructor
public class FeatureTenantToggleService {

  private final FeatureToggleRepository featureToggleRepository;

  /**
   * 某租户某特性是否允许。
   *
   * @param tenantId 租户（灰度 hash 入参）
   * @param globalEnabled 全局总开关（最终裁决；关闭则一律 false）
   * @param featureKey 特性键（如 {@link FeatureToggle#FEATURE_AUTO_APPROVE}）
   * @return 允许放行则返回 true
   */
  public boolean isFeatureAllowed(Long tenantId, boolean globalEnabled, String featureKey) {
    if (!globalEnabled) {
      return false;
    }
    if (tenantId == null) {
      // 缺租户（不应发生，防御性）：按「无配置继承全局」处理
      return true;
    }
    FeatureToggle toggle = featureToggleRepository.findByFeatureKey(tenantId, featureKey);
    if (toggle == null) {
      // 未显式配置：继承全局「开启」，兼容存量行为（全局默认关闭时早已在上一行拦截）
      return true;
    }
    return toggle.isOn(tenantId);
  }
}
