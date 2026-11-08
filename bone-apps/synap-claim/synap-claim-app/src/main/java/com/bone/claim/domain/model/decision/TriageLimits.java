package com.bone.claim.domain.model.decision;

import com.bone.claim.domain.model.config.ClaimThresholds;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分诊阈值（§8.3 配置外部化铁律 / §11.4 阈值外置）。
 *
 * <p><b>为什么需要它</b>：契约明确要求 R-FAST / R-MAN 的阈值「经 {@code feature_toggle.config_json} 外置，改阈值不发版」。
 * 在此之前它们是 {@link DecisionTriage} 里的 {@code public static final} 常量——风险事件发生时想把自动通过口径收紧，只能改代码、走发布，
 * 恰恰是配置外部化要消除的那件事。本值对象是「阈值可配」的领域表达，{@link DecisionTriage} 据此分诊。
 *
 * <p><b>单向棘轮（ratchet）——本类最重要的规则</b>：配置只允许让自动通过<b>更难</b>，不允许更容易。
 *
 * <ul>
 *   <li>金额上限只能调低（{@code autoAmountLimit ≤ 基线}）；双人复核门同样只能收紧（{@code dualReviewAmount ≤ 基线}）；
 *   <li>置信度门槛只能调高（{@code aiManualThreshold / aiReviewThreshold / ocrManualThreshold ≥ 基线}）。
 * </ul>
 *
 * <p>越界项被<b>拒绝并回落基线</b>，而不是静默钳制——静默钳制会让运维以为「放宽已生效」，实际没生效，是最坏的失败形态。 拒绝原因随 {@link
 * Resolution#rejected()} 返回，供调用方落日志与审计。
 *
 * <p><b>为什么不允许放宽</b>：放宽等于扩大自动放款的资金敞口。这条路径必须经过代码评审、回归测试与变更管理；若配置通道也能放宽， flag 就成了「绕过发布流程放大敞口」的后门——MRM
 * 与《金发 8 号》盯的正是这类「策略可被配置绕过」。收紧则相反：它只会减少自动通过的 案件数，最坏后果是人工量上升，属可逆、无损的安全方向。
 *
 * <p><b>未知键不生效但不静默</b>：拼错的键（如 {@code autoAmmountLimit}）会被登记进 {@code rejected}，
 * 否则「配了没生效」将无从发现。缺键＝用基线，这是正常状态，不算拒绝。
 *
 * @param autoAmountLimit 自动通过金额上限（元，≥ 即转复核档）
 * @param aiManualThreshold AI 置信度强制人工线（低于即转人工）
 * @param aiReviewThreshold AI 置信度复核档上界（低于即进复核档）
 * @param ocrManualThreshold OCR 置信度独立熔断线（低于即转人工）
 * @param dualReviewAmount 双人复核金额门（元，≥ 即须双人认证；与 autoAmountLimit 同基线，但语义独立——前者管「是否自动通过」，
 *     后者管「是否必须双人认证」，二者共用 1 万基线但各自可配）
 */
public record TriageLimits(
    BigDecimal autoAmountLimit,
    BigDecimal aiManualThreshold,
    BigDecimal aiReviewThreshold,
    BigDecimal ocrManualThreshold,
    BigDecimal dualReviewAmount) {

  /** config_json 键：自动通过金额上限（元）。 */
  public static final String CONFIG_AUTO_AMOUNT_LIMIT = "autoAmountLimit";

  /** config_json 键：AI 置信度强制人工线。 */
  public static final String CONFIG_AI_MANUAL_THRESHOLD = "aiManualThreshold";

  /** config_json 键：AI 置信度复核档上界。 */
  public static final String CONFIG_AI_REVIEW_THRESHOLD = "aiReviewThreshold";

  /** config_json 键：OCR 置信度熔断线。 */
  public static final String CONFIG_OCR_MANUAL_THRESHOLD = "ocrManualThreshold";

  /** config_json 键：双人复核金额门（元，≥ 即须双人认证）。 */
  public static final String CONFIG_DUAL_REVIEW_AMOUNT = "dualReviewAmount";

  /** 全部受支持的键（用于区分「未知键」与「缺键」）。 */
  private static final List<String> SUPPORTED_KEYS =
      List.of(
          CONFIG_AUTO_AMOUNT_LIMIT,
          CONFIG_AI_MANUAL_THRESHOLD,
          CONFIG_AI_REVIEW_THRESHOLD,
          CONFIG_OCR_MANUAL_THRESHOLD,
          CONFIG_DUAL_REVIEW_AMOUNT);

  public TriageLimits {
    requireNonNull(autoAmountLimit, "autoAmountLimit");
    requireNonNull(aiManualThreshold, "aiManualThreshold");
    requireNonNull(aiReviewThreshold, "aiReviewThreshold");
    requireNonNull(ocrManualThreshold, "ocrManualThreshold");
    requireNonNull(dualReviewAmount, "dualReviewAmount");
  }

  private static void requireNonNull(BigDecimal value, String name) {
    if (value == null) {
      throw new IllegalArgumentException("TriageLimits." + name + " 不得为 null");
    }
  }

  /**
   * 代码基线（未配置时的行为）。
   *
   * <p>与 {@link DecisionTriage} 的公开常量同源，保证「不加配置 ⇒ 分诊结果逐位不变」；基线本身也是「只能更严」的比较基准。
   */
  public static TriageLimits defaults() {
    return new TriageLimits(
        DecisionTriage.AUTO_AMOUNT_LIMIT,
        DecisionTriage.AI_MANUAL_THRESHOLD,
        DecisionTriage.AI_REVIEW_THRESHOLD,
        DecisionTriage.OCR_MANUAL_THRESHOLD,
        ClaimThresholds.DUAL_REVIEW_AMOUNT);
  }

  /**
   * 基线包装（供无配置路径与单测 lambda 使用，语义等价于 {@code new Resolution(defaults(), Map.of())}）。
   *
   * @return 阈值全为基线、无拒绝项
   */
  public static Resolution defaultResolution() {
    return new Resolution(defaults(), Map.of());
  }

  /**
   * 在基线之上应用覆盖值，逐项校验「只收紧」。
   *
   * @param overrides 解析自 {@code config_json} 的键值对（值可为 {@link Number} 或 {@link String}）；null /
   *     空即返回基线
   * @return 生效阈值 + 被拒绝项（键 → 原因）；被拒绝项<b>不生效</b>，按基线走
   */
  public static Resolution apply(Map<String, Object> overrides) {
    TriageLimits base = defaults();
    if (overrides == null || overrides.isEmpty()) {
      return new Resolution(base, Map.of());
    }
    Map<String, String> rejected = new LinkedHashMap<>();

    // 未知键先登记：拼错键是「配了不生效」的头号成因，必须在日志里可见
    for (String key : overrides.keySet()) {
      if (!SUPPORTED_KEYS.contains(key)) {
        rejected.put(key, "未知键（受支持：" + String.join(" / ", SUPPORTED_KEYS) + "），未生效");
      }
    }

    BigDecimal amount =
        tightestAmount(
            base.autoAmountLimit(),
            numeric(overrides, CONFIG_AUTO_AMOUNT_LIMIT, rejected),
            CONFIG_AUTO_AMOUNT_LIMIT,
            rejected);
    BigDecimal aiManual =
        strictestConfidence(
            base.aiManualThreshold(),
            numeric(overrides, CONFIG_AI_MANUAL_THRESHOLD, rejected),
            CONFIG_AI_MANUAL_THRESHOLD,
            rejected);
    BigDecimal aiReview =
        strictestConfidence(
            base.aiReviewThreshold(),
            numeric(overrides, CONFIG_AI_REVIEW_THRESHOLD, rejected),
            CONFIG_AI_REVIEW_THRESHOLD,
            rejected);
    BigDecimal ocrManual =
        strictestConfidence(
            base.ocrManualThreshold(),
            numeric(overrides, CONFIG_OCR_MANUAL_THRESHOLD, rejected),
            CONFIG_OCR_MANUAL_THRESHOLD,
            rejected);
    BigDecimal dualReview =
        tightestAmount(
            base.dualReviewAmount(),
            numeric(overrides, CONFIG_DUAL_REVIEW_AMOUNT, rejected),
            CONFIG_DUAL_REVIEW_AMOUNT,
            rejected);

    // 序关系：人工线 > 复核线上界会让「复核档」永远不可达（0.9 / 0.8 ⇒ 所有 <0.9 都直转人工）。
    // 单独调一个越界项时不整组回滚，而是把越界的那一项退回基线并登记——另一项的有效配置不该被牵连。
    if (aiManual.compareTo(aiReview) > 0) {
      if (overrides.containsKey(CONFIG_AI_MANUAL_THRESHOLD)) {
        rejected.put(
            CONFIG_AI_MANUAL_THRESHOLD,
            "不得高于 "
                + CONFIG_AI_REVIEW_THRESHOLD
                + "（否则复核档不可达，全部直转人工）；已回落基线 "
                + base.aiManualThreshold());
        aiManual = base.aiManualThreshold();
      } else {
        rejected.put(
            CONFIG_AI_REVIEW_THRESHOLD,
            "不得低于 " + CONFIG_AI_MANUAL_THRESHOLD + "（否则复核档不可达）；已回落基线 " + base.aiReviewThreshold());
        aiReview = base.aiReviewThreshold();
      }
      // 回落基线后仍可能冲突（两个配置互相挤到同一侧）⇒ 该项一并退回基线，保证终态自洽
      if (aiManual.compareTo(aiReview) > 0) {
        rejected.put(CONFIG_AI_MANUAL_THRESHOLD, "与复核线上界冲突，已回落基线");
        aiManual = base.aiManualThreshold();
      }
    }

    return new Resolution(
        new TriageLimits(amount, aiManual, aiReview, ocrManual, dualReview), rejected);
  }

  /** 金额上限：只允许收紧（≤ 基线）。放宽必须走代码发布，见类注释。 */
  private static BigDecimal tightestAmount(
      BigDecimal base, BigDecimal candidate, String key, Map<String, String> rejected) {
    if (candidate == null) {
      return base;
    }
    if (candidate.signum() <= 0) {
      rejected.put(
          key, "必须为正数（≤0 会让所有案件都落在金额闸之上：" + "自动通过与复核档全被拦，等同关闭自动放款）；已回落基线 " + base.toPlainString());
      return base;
    }
    if (candidate.compareTo(base) > 0) {
      rejected.put(
          key, "不得高于代码基线 " + base.toPlainString() + "（放宽会扩大自动放款资金敞口，须走代码发布与变更管理，不能用配置绕过）；已回落基线");
      return base;
    }
    return candidate;
  }

  /** 置信度门槛：只允许调高（≥ 基线）。调低等于放宽，同上。 */
  private static BigDecimal strictestConfidence(
      BigDecimal base, BigDecimal candidate, String key, Map<String, String> rejected) {
    if (candidate == null) {
      return base;
    }
    if (candidate.signum() <= 0 || candidate.compareTo(BigDecimal.ONE) > 0) {
      rejected.put(key, "必须在 (0, 1] 区间（置信度是 0–1 的比例，越界即判据失效）；已回落基线 " + base.toPlainString());
      return base;
    }
    if (candidate.compareTo(base) < 0) {
      rejected.put(key, "不得低于代码基线 " + base.toPlainString() + "（调低会让更多案件越过人工线，属放宽敞口，须走代码发布）；已回落基线");
      return base;
    }
    return candidate;
  }

  /** 数值容错：JSON 数字反序列化为 BigDecimal/Integer/Double，也接受字符串（配置面板常以字符串提交）。 */
  private static BigDecimal decimalOf(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof BigDecimal bd) {
      return bd;
    }
    if (raw instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    String text = raw.toString().trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 取某键的数值，并把「键在、值却无法解析」登记为拒绝。
   *
   * <p>为什么不能直接返回 null 了事：null 在 {@link #apply} 里与「缺键」不可区分，非数字配置就会被<b>静默</b>丢弃，
   * 运维看到「配了」而系统当「没配」——正是本类要消灭的失败形态。缺键（不含该 key）才是正常状态，不登记。
   */
  private static BigDecimal numeric(
      Map<String, Object> overrides, String key, Map<String, String> rejected) {
    if (!overrides.containsKey(key)) {
      return null;
    }
    Object raw = overrides.get(key);
    BigDecimal value = decimalOf(raw);
    if (value == null) {
      rejected.put(key, "不是合法数值（原始值 \"" + raw + "\"）；该键未生效，已回落代码基线 " + defaultsForKey(key));
    }
    return value;
  }

  /** 某键对应的基线值（拒绝原因里给出「没配时是多少」，便于运维直接改对）。 */
  private static String defaultsForKey(String key) {
    TriageLimits base = defaults();
    return switch (key) {
      case CONFIG_AUTO_AMOUNT_LIMIT -> base.autoAmountLimit().toPlainString();
      case CONFIG_AI_MANUAL_THRESHOLD -> base.aiManualThreshold().toPlainString();
      case CONFIG_AI_REVIEW_THRESHOLD -> base.aiReviewThreshold().toPlainString();
      case CONFIG_OCR_MANUAL_THRESHOLD -> base.ocrManualThreshold().toPlainString();
      case CONFIG_DUAL_REVIEW_AMOUNT -> base.dualReviewAmount().toPlainString();
      default -> "";
    };
  }

  /**
   * 应用结果。
   *
   * @param limits 实际生效的阈值（被拒绝项已回落基线）
   * @param rejected 未生效的键 → 原因（含后果说明）；空表示全部生效或无配置
   */
  public record Resolution(TriageLimits limits, Map<String, String> rejected) {

    /** 是否全部配置项均生效（无拒绝）。 */
    public boolean fullyApplied() {
      return rejected.isEmpty();
    }

    /** 拒绝项的紧凑摘要（日志用，形如 {@code key: reason; key2: reason2}）。 */
    public String rejectionSummary() {
      if (rejected.isEmpty()) {
        return "";
      }
      List<String> parts = new ArrayList<>(rejected.size());
      rejected.forEach((k, v) -> parts.add(k + ": " + v));
      return String.join("; ", parts);
    }
  }
}
