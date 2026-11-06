package com.bone.claim.domain.model.evaluation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 模型卡与模型清单（G4-2 MRM 最小契约：NIST AI RMF GOVERN / SR 11-7 三道防线 / B7）。
 *
 * <p><b>为什么要有这张卡</b>：§11.3 已把「模型卡、模型清单、独立验证报告、漂移监控」列为 L3 待办， 但代码侧只有 {@code modelVersion} / {@code
 * promptVersion} 的轨迹留痕——能回放「哪个模型跑过」， 却回答不了「这个模型被谁验证过、有什么局限、什么时候该退役」。监管要求的是后者 （金发〔2026〕8 号 AI
 * 治理架构、监管总局 32 项）。本类把最小字段做成<b>构造期强约束</b>： 字段不全或未经独立验证的模型卡，结构上就不能被判定为「可进生产」。
 *
 * <p><b>三道防线不可跳过</b>：一线自测（开发）→ 二线独立验证（不参与开发者）→ 三线内审。 只有达到 {@link Verification#INDEPENDENT_VERIFIED}
 * 及以上才允许进放量档； 这不是流程建议，是 {@link #eligibleForProduction(LocalDate)} 的硬判据。
 *
 * <p><b>不落库、不接 DDL</b>：模型卡是治理资产，首版由评测侧以清单形式维护； 判据在构造期生效，避免为一张卡引入迁移与权限面。
 */
public final class AiModelCard {

  /** 冻结集哈希格式：与 {@code frozen_set.py} V3/V5 的 sha256 一致。 */
  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

  private AiModelCard() {}

  /** 验证状态（顺序即防线层级，不可跳级）。 */
  public enum Verification {
    /** 仅一线开发自测。 */
    SELF_TEST_ONLY,
    /** 二线独立验证通过（模型生效的最低门槛）。 */
    INDEPENDENT_VERIFIED,
    /** 三线内审通过。 */
    INTERNAL_AUDITED;

    public boolean atLeastIndependentVerification() {
      return this == INDEPENDENT_VERIFIED || this == INTERNAL_AUDITED;
    }
  }

  /** 漂移结论：PSI 超阈值不自动换模型，先收紧放量并触发复验（E4 MRM 闭环）。 */
  public enum DriftVerdict {
    /** 无观测数据，不给「稳定」结论。 */
    NO_DATA,
    WITHIN_TOLERANCE,
    /** 接近阈值：观察 + 加密采样。 */
    WATCH,
    /** 超阈值：阻断晋升并触发复验。 */
    BREACH;

    public boolean blocksPromotion() {
      return this == WATCH || this == BREACH;
    }
  }

  /**
   * 最小模型卡。
   *
   * @param modelId 模型标识（含版本），清单内唯一
   * @param promptVersion 提示词版本（与轨迹 {@code aiPromptVersion} 对齐）
   * @param intendedUse 预期用途（超出即须重新验证）
   * @param trainingDataLineage 训练数据来源/权属/脱敏/时效谱系
   * @param evalSetHash 冻结评测集 sha256
   * @param metricSlices 切片指标摘要（金额段 × 医院等级 × 渠道 × 风险标记）
   * @param knownLimitations 已文档化的局限（不得为空——「无局限」不是可接受答案）
   * @param retirementCondition 退役条件
   * @param owner 责任人
   * @param verification 验证状态
   * @param nextReviewDate 下次复验日
   */
  public record ModelCard(
      String modelId,
      String promptVersion,
      String intendedUse,
      String trainingDataLineage,
      String evalSetHash,
      String metricSlices,
      String knownLimitations,
      String retirementCondition,
      String owner,
      Verification verification,
      LocalDate nextReviewDate) {

    public ModelCard {
      requireText(modelId, "modelId");
      requireText(promptVersion, "promptVersion");
      requireText(intendedUse, "intendedUse");
      requireText(trainingDataLineage, "trainingDataLineage");
      requireText(metricSlices, "metricSlices");
      requireText(knownLimitations, "knownLimitations");
      requireText(retirementCondition, "retirementCondition");
      requireText(owner, "owner");
      Objects.requireNonNull(evalSetHash, "evalSetHash 不可为 null");
      if (!SHA256.matcher(evalSetHash).matches()) {
        throw new IllegalArgumentException("evalSetHash 须为 64 位小写 sha256：" + evalSetHash);
      }
      Objects.requireNonNull(verification, "verification 不可为 null");
      Objects.requireNonNull(nextReviewDate, "nextReviewDate 不可为 null");
    }

    /** 是否允许进入放量档：独立验证 + 未过复验期。 */
    public boolean eligibleForProduction(LocalDate asOf) {
      Objects.requireNonNull(asOf, "asOf 不可为 null");
      return verification.atLeastIndependentVerification() && !nextReviewDate.isBefore(asOf);
    }

    /** 复验是否已到期。 */
    public boolean reviewOverdue(LocalDate asOf) {
      return nextReviewDate.isBefore(asOf);
    }
  }

  /** 模型清单校验结果：重复标识、未验证、复验到期分别计数，不混成一个数。 */
  public record InventoryReport(
      int total, int duplicateIds, int unverified, int reviewOverdue, List<String> blockers) {

    public InventoryReport {
      blockers = List.copyOf(blockers);
    }

    public boolean clean() {
      return duplicateIds == 0 && unverified == 0 && reviewOverdue == 0;
    }
  }

  /** 生成模型清单报告：同一 {@code modelId} 重复即判红（清单是治理真源，不能有两套口径）。 */
  public static InventoryReport inventory(List<ModelCard> cards, LocalDate asOf) {
    Objects.requireNonNull(cards, "cards 不可为 null");
    Objects.requireNonNull(asOf, "asOf 不可为 null");
    Map<String, Integer> seen = new LinkedHashMap<>();
    List<String> blockers = new ArrayList<>();
    int duplicates = 0;
    int unverified = 0;
    int overdue = 0;
    for (ModelCard c : cards) {
      if (c == null) {
        blockers.add("清单含 null 条目");
        continue;
      }
      if (seen.merge(c.modelId(), 1, Integer::sum) > 1) {
        duplicates++;
        blockers.add("重复模型标识：" + c.modelId());
      }
      if (!c.verification().atLeastIndependentVerification()) {
        unverified++;
        blockers.add("未经二线独立验证：" + c.modelId());
      }
      if (c.reviewOverdue(asOf)) {
        overdue++;
        blockers.add("复验已到期：" + c.modelId());
      }
    }
    return new InventoryReport(cards.size(), duplicates, unverified, overdue, blockers);
  }

  /**
   * 漂移判据（PSI，Population Stability Index）。
   *
   * <p>空值给 {@code NO_DATA} ——「没测过漂移」不能被显示成「漂移在容差内」。 超阈值仅阻断晋升与触发复验，<b>不自动切换模型</b>：换模型须经
   * champion/challenger 对照 （{@link AiConfidenceCalibration#challengerAdmissible}）与独立验证。
   */
  public static DriftVerdict drift(BigDecimal psi, BigDecimal watchRatio, BigDecimal threshold) {
    Objects.requireNonNull(watchRatio, "watchRatio 不可为 null");
    Objects.requireNonNull(threshold, "threshold 不可为 null");
    if (psi == null) {
      return DriftVerdict.NO_DATA;
    }
    if (psi.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("PSI 不得为负：" + psi);
    }
    if (watchRatio.compareTo(threshold) >= 0) {
      throw new IllegalArgumentException("观察阈值须严格小于阻断阈值");
    }
    if (psi.compareTo(threshold) >= 0) {
      return DriftVerdict.BREACH;
    }
    if (psi.compareTo(watchRatio) >= 0) {
      return DriftVerdict.WATCH;
    }
    return DriftVerdict.WITHIN_TOLERANCE;
  }

  private static void requireText(String value, String field) {
    Objects.requireNonNull(value, field + " 不可为 null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " 不可为空");
    }
  }
}
