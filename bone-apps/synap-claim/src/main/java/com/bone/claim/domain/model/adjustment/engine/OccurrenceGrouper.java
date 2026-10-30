package com.bone.claim.domain.model.adjustment.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 「次」的分组（V1 §7.3 六档）。
 *
 * <p><b>语义上最容易做错的一点</b>：次限额不是"对总额再取一次 min"，而是<b>先按「次」的定义分组，每组独立走一遍完整理算</b> （含各自的免赔与比例），每组各自受次限额约束。
 *
 * <p>举个会翻车的例子：两天的门诊各 500，次限额 300。
 *
 * <ul>
 *   <li>正确（分组）：两组各 min(500, 300) = 300，合计 600；
 *   <li>错误（总额取 min）：1000 min 300 = 300，少赔 300。
 * </ul>
 *
 * <p>本类只做分组，不算钱——"每组独立理算"由 {@link AdjustmentEngine} 编排，避免分组与算钱两处各写一遍顺序。
 */
public final class OccurrenceGrouper {

  /** 「次」的定义（V1 §7.3 六档）。 */
  public enum Basis {
    /** 无次赔付限额：跳过本逻辑。 */
    NONE,
    /** 一赔案计一次。 */
    PER_CLAIM,
    /** 同赔案且发票同日计一次。 */
    SAME_DAY,
    /** 同赔案且发票同日同院计一次。 */
    SAME_DAY_HOSPITAL,
    /** 同赔案且发票同日同院同科室计一次。 */
    SAME_DAY_HOSPITAL_DEPT,
    /** 同赔案且发票同日同院同病种计一次。 */
    SAME_DAY_HOSPITAL_DISEASE
  }

  /**
   * 一条费用事实（分组维度来源）。
   *
   * <p>字段缺失时按"空字符串"参与分组键：{@code null} 与 {@code ""} 视为同一组， 避免同一张票因字段为空被拆成两组而多赔一次次限额。
   */
  public record Fact(
      String claimNo, LocalDate invoiceDate, String hospital, String department, String disease) {

    public Fact {
      claimNo = claimNo == null ? "" : claimNo;
      hospital = hospital == null ? "" : hospital;
      department = department == null ? "" : department;
      disease = disease == null ? "" : disease;
    }

    public static Fact ofClaim(String claimNo) {
      return new Fact(claimNo, null, null, null, null);
    }

    /** 按给定档位生成分组键。 */
    public String keyOf(Basis basis) {
      return switch (basis) {
        case NONE -> "*";
        case PER_CLAIM -> claimNo;
        case SAME_DAY -> claimNo + "|" + datePart();
        case SAME_DAY_HOSPITAL -> claimNo + "|" + datePart() + "|" + hospital;
        case SAME_DAY_HOSPITAL_DEPT -> claimNo
            + "|"
            + datePart()
            + "|"
            + hospital
            + "|"
            + department;
        case SAME_DAY_HOSPITAL_DISEASE -> claimNo
            + "|"
            + datePart()
            + "|"
            + hospital
            + "|"
            + disease;
      };
    }

    private String datePart() {
      return invoiceDate == null ? "" : invoiceDate.toString();
    }
  }

  /** 一个「次」分组（键 + 成员下标，下标与输入顺序一一对应）。 */
  public record Occurrence(String key, List<Integer> memberIndexes) {

    public Occurrence {
      memberIndexes = List.copyOf(memberIndexes);
    }
  }

  private OccurrenceGrouper() {}

  /**
   * 按档位分组。
   *
   * @param basis 「次」的定义
   * @param facts 每条费用事实（顺序须与明细一致）
   * @return 分组列表（保持首次出现顺序，保证同输入同输出）
   */
  public static List<Occurrence> group(Basis basis, List<Fact> facts) {
    if (basis == null || basis == Basis.NONE || facts.isEmpty()) {
      return List.of(new Occurrence("*", indexes(facts.size())));
    }
    Map<String, List<Integer>> buckets = new LinkedHashMap<>();
    for (int i = 0; i < facts.size(); i++) {
      String key = facts.get(i).keyOf(basis);
      buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
    }
    List<Occurrence> occurrences = new ArrayList<>(buckets.size());
    buckets.forEach((key, indexes) -> occurrences.add(new Occurrence(key, indexes)));
    return occurrences;
  }

  /** 分组键数量（用于判断是否真的需要分组理算）。 */
  public static int count(Basis basis, List<Fact> facts) {
    return group(basis, facts).size();
  }

  /** 是否需要分组（只有一个组时整案即一次，可走快路径）。 */
  public static boolean singleGroup(Basis basis, List<Fact> facts) {
    return count(basis, facts) <= 1;
  }

  private static List<Integer> indexes(int size) {
    List<Integer> indexes = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      indexes.add(i);
    }
    return indexes;
  }

  /** 校验：配了次限额却没配「次」的定义，属"配了不生效"。 */
  public static void requireConsistent(Basis basis, java.math.BigDecimal occurrenceLimit) {
    boolean hasLimit = occurrenceLimit != null && occurrenceLimit.signum() > 0;
    if (hasLimit && (basis == null || basis == Basis.NONE)) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "配置了次限额（%s）但「次」的定义为无：该限额永远不会生效",
          occurrenceLimit.toPlainString());
    }
    Objects.requireNonNull(basis, "次定义不得为 null");
  }

  /**
   * 校验「次」的判定维度确实存在于明细事实里。
   *
   * <p><b>为什么必须拒绝而不是照算</b>：分组键在维度缺失时退化成空串（{@link Fact#keyOf}），于是"同日同院" 的全部明细落进同一个组——次限额只被应用一次。 两天各
   * 500、次限额 300 的场景会赔 300 而不是 600，属<b>静默少赔</b>；反过来若额度按责任共享， 单组又会把两者的额度合并计算而超赔。两者都不会报错，只会在对账时被发现。
   *
   * <p>缺一个维度就整体拒绝（而不是"能分几组算几组"）：混合分组会把一张票拆成"有日期的组"与"日期未知的组"， 后者拿到的是一次独立的次限额——同一份条款两种算法。
   *
   * @param basis 「次」的定义
   * @param facts 明细事实（顺序与明细一致）
   * @throws RuleGap 任一事实缺少该档位要求的维度
   */
  public static void requireDimensions(Basis basis, List<Fact> facts) {
    if (basis == null || basis == Basis.NONE || basis == Basis.PER_CLAIM || facts.isEmpty()) {
      return;
    }
    int missing = 0;
    for (Fact fact : facts) {
      if (!hasDimension(basis, fact)) {
        missing++;
      }
    }
    if (missing > 0) {
      throw RuleGap.of(
          RuleGap.Code.OCCURRENCE_DIMENSION_MISSING,
          "「次」定义为 %s，但 %d/%d 条明细缺少该判定维度（%s）：按一组计算会少赔，须先补齐明细维度",
          basis,
          missing,
          facts.size(),
          requiredDimension(basis));
    }
  }

  private static boolean hasDimension(Basis basis, Fact fact) {
    return switch (basis) {
      case NONE, PER_CLAIM -> true;
      case SAME_DAY -> fact.invoiceDate() != null;
      case SAME_DAY_HOSPITAL -> fact.invoiceDate() != null && !fact.hospital().isBlank();
      case SAME_DAY_HOSPITAL_DEPT -> fact.invoiceDate() != null
          && !fact.hospital().isBlank()
          && !fact.department().isBlank();
      case SAME_DAY_HOSPITAL_DISEASE -> fact.invoiceDate() != null
          && !fact.hospital().isBlank()
          && !fact.disease().isBlank();
    };
  }

  private static String requiredDimension(Basis basis) {
    return switch (basis) {
      case NONE, PER_CLAIM -> "";
      case SAME_DAY -> "发票日期";
      case SAME_DAY_HOSPITAL -> "发票日期+医院";
      case SAME_DAY_HOSPITAL_DEPT -> "发票日期+医院+科室";
      case SAME_DAY_HOSPITAL_DISEASE -> "发票日期+医院+病种";
    };
  }
}
