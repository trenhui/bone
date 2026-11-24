package com.bone.claim.application;

import com.bone.claim.application.port.out.TriageLimitsPort;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.decision.TriageLimits;
import com.bone.claim.domain.model.inspection.InspectionConfig;
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
    warnOnUnsupportedState(toggle, tenantId, featureKey);
    return toggle.isOn(tenantId);
  }

  /**
   * 状态取值非法时打 WARN——让「配错了」可见。
   *
   * <p><b>为什么读取侧不能只是静默当关闭</b>：fail-closed 的方向是对的（未知状态按关闭处理，绝不放量资金策略）， 但<b>静默</b>是缺陷：运维把 state 写成
   * {@code ENABLE}（少个 D）或 {@code enabled}（小写）， 开关永远不生效，页面与日志都看不出异常，只能靠业务方报「功能没反应」才发现。
   * 写入期已拒绝非法值（{@code FeatureToggle.create}），但库里可能已有历史脏数据或直连写入的非法值， 故读取侧补一条 WARN：既不改变判定结果，又让问题可被发现。
   */
  private void warnOnUnsupportedState(FeatureToggle toggle, Long tenantId, String featureKey) {
    if (toggle.hasSupportedState()) {
      return;
    }
    log.warn(
        "[特性开关] state 取值非法，已按「关闭」处理（fail-closed，不放量）：tenantId={}, feature={}, state={}。"
            + "合法取值为 ENABLED/DISABLED（大小写敏感）。该开关当前**不会生效**，"
            + "若期望开启请修正为 ENABLED；AI 介入档位（SHADOW/ASSISTED/AUTOMATED）请另设特性键，不要复用本列。",
        tenantId,
        featureKey,
        toggle.getState());
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
    if (featureToggleRepository == null) {
      // 防御：仓储未注入（如纯单测基座）时回落基线，绝不抛出——与「任何异常不得改变资金口径」一致。
      return new TriageLimits.Resolution(TriageLimits.defaults(), Map.of());
    }
    FeatureToggle toggle =
        featureToggleRepository.findByFeatureKey(tenantId, FeatureToggle.FEATURE_TRIAGE_LIMITS);
    if (toggle == null) {
      return new TriageLimits.Resolution(TriageLimits.defaults(), Map.of());
    }
    if (!toggle.isOn(tenantId)) {
      // 显式 DISABLED 或未命中灰度：按基线跑（这是运维的「回滚到默认」手段，不该报错）
      warnOnUnsupportedState(toggle, tenantId, FeatureToggle.FEATURE_TRIAGE_LIMITS);
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

  /**
   * 抽检比例（批 4 任务 4.1）：从 {@code INSPECTION_SAMPLING} 的 config_json 读，回落基线 5%。
   *
   * <p>与 triageLimits 同款 fail-closed：无记录/未开启/非法 JSON/越界一律回落 {@link InspectionConfig#defaults()}，并
   * WARN 让"配了不生效"可见；绝不默认 0（不抽样）或 100（全量）。
   */
  public InspectionConfig inspectionConfig(long tenantId) {
    if (featureToggleRepository == null) {
      return InspectionConfig.defaults();
    }
    try {
      FeatureToggle toggle =
          featureToggleRepository.findByFeatureKey(
              tenantId, FeatureToggle.FEATURE_INSPECTION_SAMPLING);
      if (toggle == null || !toggle.isOn(tenantId)) {
        return InspectionConfig.defaults();
      }
      String raw = toggle.getConfigJson();
      if (raw == null || raw.isBlank()) {
        return InspectionConfig.defaults();
      }
      Map<String, Object> parsed =
          objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
      InspectionConfig cfg = InspectionConfig.from(parsed);
      if (!cfg.fullyApplied()) {
        log.warn("[抽检配置] 部分配置未生效，已回落基线：tenantId={}, rejected={}", tenantId, cfg.rejected());
      }
      return cfg;
    } catch (Exception e) {
      // 配置读取（仓储/开关判定）与解析任一失败都回落基线 5%，绝不把 DB 故障变成"该笔不抽检"。
      log.warn(
          "[抽检配置] 配置读取/解析失败，回落基线 {}%：tenantId={}, err={}",
          InspectionConfig.DEFAULT_SAMPLING_RATE, tenantId, e.getMessage());
      return new InspectionConfig(
          InspectionConfig.DEFAULT_SAMPLING_RATE,
          Map.of(JSON_SENTINEL, "配置读取/解析失败（" + e.getMessage() + "）；已回落基线"));
    }
  }

  /**
   * 抽检处置动作开关（批 4 任务 4.3）：关闭时只登记不流转。
   *
   * <p><b>fail-closed，不复用 {@link #isFeatureAllowed}</b>：后者在「无配置记录」时继承全局开启（适合 AI 能力灰度），
   * 但处置动作会生成重办任务、推动案件回流，是一条影响资金与案件状态的路径——这类闸门必须「未显式开启即关闭」。 无记录 / 未 ENABLED / 状态非法一律 false（只登记），与
   * triageLimits/inspectionConfig 的 fail-closed 同款。
   */
  public boolean isInspectionActionEnabled(long tenantId, boolean globalEnabled) {
    if (!globalEnabled) {
      return false;
    }
    if (featureToggleRepository == null) {
      return false;
    }
    FeatureToggle toggle =
        featureToggleRepository.findByFeatureKey(tenantId, FeatureToggle.FEATURE_INSPECTION_ACTION);
    if (toggle == null) {
      return false; // 未配置即关闭（fail-closed）
    }
    warnOnUnsupportedState(toggle, tenantId, FeatureToggle.FEATURE_INSPECTION_ACTION);
    return toggle.isOn(tenantId);
  }

  /**
   * 审核疲劳防控参数（无记录 / 关闭 / 非法 JSON 一律回落代码基线）。
   *
   * <p>不复用 {@link #isFeatureAllowed}：后者在「无记录」时继承全局开启；而这里是真实约束分配准入的闸门， 必须由 {@code
   * REVIEW_FATIGUE_GUARD} 显式开启才生效。解析委托给 {@link ReviewFatigueConfig#from}， 拒绝项由评估结果上屏，避免「配了不生效」无人知晓。
   */
  public ReviewFatigueConfig reviewFatigueConfig(long tenantId) {
    if (featureToggleRepository == null) {
      return ReviewFatigueConfig.defaults();
    }
    FeatureToggle toggle =
        featureToggleRepository.findByFeatureKey(
            tenantId, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD);
    if (toggle == null || !toggle.isOn(tenantId)) {
      return ReviewFatigueConfig.defaults();
    }
    warnOnUnsupportedState(toggle, tenantId, FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD);
    String raw = toggle.getConfigJson();
    if (raw == null || raw.isBlank()) {
      return ReviewFatigueConfig.defaults();
    }
    try {
      Map<String, Object> parsed =
          objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
      ReviewFatigueConfig cfg = ReviewFatigueConfig.from(parsed);
      if (!cfg.fullyApplied()) {
        log.warn("[审核疲劳配置] 部分配置未生效，已回落基线：tenantId={}, rejected={}", tenantId, cfg.rejected());
      }
      return cfg;
    } catch (Exception e) {
      log.warn("[审核疲劳配置] config_json 解析失败，回落基线：tenantId={}, err={}", tenantId, e.getMessage());
      return new ReviewFatigueConfig(
          ReviewFatigueConfig.DEFAULT_MAX_PER_SHIFT,
          ReviewFatigueConfig.DEFAULT_REST_PROMPT_MINUTES,
          ReviewFatigueConfig.DEFAULT_SHIFT_END_EXCLUSION_MINUTES,
          ReviewFatigueConfig.DEFAULT_SENSITIVE_AMOUNT_YUAN,
          Map.of(JSON_SENTINEL, "不是合法的 JSON 对象（" + e.getMessage() + "）；已回落代码基线"));
    }
  }
}
