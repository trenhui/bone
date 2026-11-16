package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.decision.TriageLimits;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 配置治理受控写（批 2，方案 A）。
 *
 * <p><b>为什么棘轮必须相对「当前生效值」而不是复用 {@link TriageLimits#apply(Map)} 的基线校验</b>：{@code apply()}
 * 把配置收敛到代码基线，用于读取期兜底（任何异常不得改变资金口径）。但治理写要求「逐字段比较当前生效值」—— 若库里已收紧到 8000，请求 9000 虽低于代码基线
 * 10000，相对当前值仍是<b>放宽</b>。只看 {@code apply()} 会放行， 等于给棘轮开了一个「先收紧再放宽」的后门。故本服务先拿请求原始值逐字段与当前生效值比较，再用
 * {@code apply()} 做未知键 / 序关系等合法性兜底。
 *
 * <p>审计走 {@link FeatureToggle#overwriteConfigJson(String, String, Instant)} 的 remark 现场说明 + 结构化日志，
 * 零新增 DDL（见 FeatureToggleChangedEvent 的取舍）。写 API 开关复用 {@code t_feature_toggle}，未配置视为关闭。
 *
 * <p><b>E-5.4 豁免（技术中间态）</b>：本服务只写 {@code t_feature_toggle.config_json} 配置快照，不直接改赔案状态机
 * 或资金流水；阈值真正生效口径在读取期由 {@link FeatureTenantToggleService#triageLimits(long)} 与 {@link
 * TriageLimits#apply(Map)} 兜底收敛（异常一律回代码基线）。审计留痕由 remark + 结构化日志承担， 不进领域事件总线，故声明 {@link
 * NoDomainEvent}。
 */
@Service
@NoDomainEvent
@RequiredArgsConstructor
public class AiConfigGovernanceService {

  /** 写 API 总开关特性键：未配置 / 非 ENABLED 一律 fail-closed 为不可写。 */
  public static final String FEATURE_CONFIG_WRITE = "AI_CONFIG_WRITE";

  private final FeatureToggleRepository featureToggleRepository;
  private final FeatureTenantToggleService featureTenantToggleService;
  private final ObjectMapper objectMapper;

  /** 写入 L1/L2/L3 分诊阈值配置。只能相等或更严格；放宽、非法键、写开关关闭一律拒绝且不改库。 */
  public void updateTriageLimits(long tenantId, String operatorId, Map<String, Object> requested) {
    requireWriteEnabled(tenantId);

    if (requested == null || requested.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_INVALID);
    }

    TriageLimits current = featureTenantToggleService.triageLimits(tenantId).limits();
    ratchet(current, requested);

    TriageLimits.Resolution applied = TriageLimits.apply(requested);
    if (!applied.fullyApplied()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_INVALID);
    }

    String payload;
    try {
      payload = objectMapper.writeValueAsString(requested);
    } catch (Exception e) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_INVALID);
    }
    String auditNote =
        "operator=" + operatorId + " old=" + current + " new=" + requested + " note=仅收紧棘轮写入";
    Instant now = Instant.now();
    FeatureToggle toggle =
        featureToggleRepository.findByFeatureKey(tenantId, FeatureToggle.FEATURE_TRIAGE_LIMITS);
    if (toggle == null) {
      toggle =
          FeatureToggle.create(
              tenantId,
              FeatureToggle.FEATURE_TRIAGE_LIMITS,
              FeatureToggle.STATE_ENABLED,
              100,
              auditNote,
              payload,
              now);
    } else {
      toggle.overwriteConfigJson(payload, auditNote, now);
    }
    featureToggleRepository.save(toggle);
  }

  /** 写 API 是否开启；供治理页读取后渲染只读，消除「页面可编辑但提交失败」。 */
  public boolean isWriteEnabled(long tenantId) {
    FeatureToggle toggle = featureToggleRepository.findByFeatureKey(tenantId, FEATURE_CONFIG_WRITE);
    return toggle != null && toggle.isOn(tenantId);
  }

  /** 当前生效阈值；治理页展示用（不暴露 rejected 之外的内部状态）。 */
  public TriageLimits currentLimits(long tenantId) {
    return featureTenantToggleService.triageLimits(tenantId).limits();
  }

  private void requireWriteEnabled(long tenantId) {
    FeatureToggle toggle = featureToggleRepository.findByFeatureKey(tenantId, FEATURE_CONFIG_WRITE);
    if (toggle == null || !toggle.isOn(tenantId)) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_WRITE_DISABLED);
    }
  }

  /**
   * 逐字段比较请求原始值与当前生效值：金额类只能更小，置信度类只能更大。
   *
   * <p>读原始值而非 {@code apply()} 的回落结果——后者会把「放宽到基线以下」悄悄改写成基线，使放宽被掩盖。
   */
  private static void ratchet(TriageLimits current, Map<String, Object> requested) {
    rejectIfLoosenedAmount(
        current.autoAmountLimit(), requested, TriageLimits.CONFIG_AUTO_AMOUNT_LIMIT);
    rejectIfLoosenedAmount(
        current.dualReviewAmount(), requested, TriageLimits.CONFIG_DUAL_REVIEW_AMOUNT);
    rejectIfLoosenedConfidence(
        current.aiManualThreshold(), requested, TriageLimits.CONFIG_AI_MANUAL_THRESHOLD);
    rejectIfLoosenedConfidence(
        current.aiReviewThreshold(), requested, TriageLimits.CONFIG_AI_REVIEW_THRESHOLD);
    rejectIfLoosenedConfidence(
        current.ocrManualThreshold(), requested, TriageLimits.CONFIG_OCR_MANUAL_THRESHOLD);
  }

  private static void rejectIfLoosenedAmount(
      BigDecimal current, Map<String, Object> requested, String key) {
    BigDecimal candidate = decimal(requested, key);
    if (candidate != null && candidate.compareTo(current) > 0) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_RATCHET_VIOLATION);
    }
  }

  private static void rejectIfLoosenedConfidence(
      BigDecimal current, Map<String, Object> requested, String key) {
    BigDecimal candidate = decimal(requested, key);
    if (candidate != null && candidate.compareTo(current) < 0) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_RATCHET_VIOLATION);
    }
  }

  private static BigDecimal decimal(Map<String, Object> requested, String key) {
    Object raw = requested.get(key);
    if (raw == null) {
      return null;
    }
    try {
      return new BigDecimal(raw.toString());
    } catch (NumberFormatException e) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_CONFIG_INVALID);
    }
  }
}
