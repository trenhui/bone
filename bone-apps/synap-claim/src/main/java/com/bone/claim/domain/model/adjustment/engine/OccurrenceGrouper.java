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
   * 分组维度及其作用域。
   *
   * <p><b>{@code scope} 决定"缺失能否降级"</b>，这是本类唯一真正的设计判断：
   *
   * <ul>
   *   <li>{@link Scope#INVOICE}：发票级维度，一赔案一发票（{@code uk_tenant_invoice}）⇒ 案内恒定。
   *       若整案都缺该维度，分组键里那一段对所有成员恒为空，<b>分组结果与配置语义相同</b>，因此可以降级；
   *   <li>{@link Scope#LINE}：行级维度，一张发票可跨科室 ⇒ 缺了就<b>无法</b>证明恒定， 并成一组会把两次就诊的次限额合并成一次（少赔），必须拒绝。
   * </ul>
   */
  public enum Dimension {
    INVOICE_DATE("发票日期", Scope.INVOICE),
    HOSPITAL("医院编码", Scope.INVOICE),
    DEPARTMENT("就诊科室", Scope.LINE),
    DIAGNOSIS("病种编码", Scope.INVOICE);

    /** 维度作用域。 */
    public enum Scope {
      INVOICE,
      LINE
    }

    private final String label;
    private final Scope scope;

    Dimension(String label, Scope scope) {
      this.label = label;
      this.scope = scope;
    }

    public String label() {
      return label;
    }

    public Scope scope() {
      return scope;
    }
  }

  /** 某档「次」定义所需的维度。 */
  public static java.util.Set<Dimension> dimensionsOf(Basis basis) {
    if (basis == null) {
      return java.util.Set.of();
    }
    return switch (basis) {
      case NONE, PER_CLAIM -> java.util.Set.of();
      case SAME_DAY -> java.util.EnumSet.of(Dimension.INVOICE_DATE);
      case SAME_DAY_HOSPITAL -> java.util.EnumSet.of(Dimension.INVOICE_DATE, Dimension.HOSPITAL);
      case SAME_DAY_HOSPITAL_DEPT -> java.util.EnumSet.of(
          Dimension.INVOICE_DATE, Dimension.HOSPITAL, Dimension.DEPARTMENT);
      case SAME_DAY_HOSPITAL_DISEASE -> java.util.EnumSet.of(
          Dimension.INVOICE_DATE, Dimension.HOSPITAL, Dimension.DIAGNOSIS);
    };
  }

  /**
   * 校验「次」的判定维度是否足以支撑分组，并返回<b>可证明不影响结果</b>的降级项。
   *
   * <p><b>为什么不再"缺就拒"</b>：发票日期/医院/病种是发票级事实，一赔案一发票时案内恒定， 缺失不影响分组结果；对这类案子一律拒绝等于把"证据未回填"当成"规则不成立"，转人工率
   * 100%。 反之，若连"本案是单张发票"都证明不了（无发票号），缺失就真的可能把多天多票并成一次，必须拒绝。
   *
   * <p>三种判定：
   *
   * <ol>
   *   <li>全部有值 → 正常分组；
   *   <li>全部缺失 + 发票级 + 可证单票 → <b>降级</b>并返回说明（进快照与诊断，审计可见）；
   *   <li>部分有值（同案内不一致）、行级缺失、或不可证单票 → <b>拒绝</b>（fail-closed）。
   * </ol>
   *
   * @param basis 「次」的定义
   * @param facts 明细事实（顺序与明细一致）
   * @param singleInvoiceProvable 是否能证明本案只对应一张发票（赔案有发票号即为真）
   * @return 降级说明（空列表 = 维度完整，无需降级）
   * @throws RuleGap 维度缺失且不可证明等价，或同案内维度不一致
   */
  public static List<String> requireDimensions(
      Basis basis, List<Fact> facts, boolean singleInvoiceProvable) {
    if (basis == null || basis == Basis.NONE || facts.isEmpty()) {
      return List.of();
    }
    List<String> degraded = new ArrayList<>();
    for (Dimension dimension : dimensionsOf(basis)) {
      int missing = countMissing(dimension, facts);
      if (missing == 0) {
        continue;
      }
      if (dimension.scope() == Dimension.Scope.LINE) {
        throw RuleGap.of(
            RuleGap.Code.OCCURRENCE_DIMENSION_MISSING,
            "「次」定义为 %s，需要%s，但 %d/%d 条明细缺失：该维度是行级事实（一张发票可跨%s），" + "缺失时并成一组会把多次的次限额合并成一次（少赔），须先补齐明细",
            basis,
            dimension.label(),
            missing,
            facts.size(),
            dimension.label());
      }
      if (missing < facts.size()) {
        // 同案内有的行有、有的行没有：数据本身不自洽，任何分组结果都建立在"猜哪些行算另一次"上。
        throw RuleGap.of(
            RuleGap.Code.OCCURRENCE_DIMENSION_INCONSISTENT,
            "「次」定义为 %s，%s 在 %d/%d 条明细上缺失（其余有值）：同一赔案内该维度不一致，"
                + "按存在与否分组会把同一张票拆成两个「次」（多赔一次次限额），须先修数据",
            basis,
            dimension.label(),
            missing,
            facts.size());
      }
      if (!singleInvoiceProvable) {
        throw RuleGap.of(
            RuleGap.Code.OCCURRENCE_DIMENSION_MISSING,
            "「次」定义为 %s，需要%s，但全部 %d 条明细都缺失，且本案无发票号可证"
                + "「案内只有一张发票」："
                + "无法排除多天/多票混装，按一次计算会少赔，须补发票维度或发票号",
            basis,
            dimension.label(),
            facts.size());
      }
      degraded.add(
          dimension.label()
              + "缺失 "
              + facts.size()
              + "/"
              + facts.size()
              + "（发票级维度，有发票号故案内恒定，按同一次处理，不改变分组结果）");
    }
    return degraded;
  }

  private static int countMissing(Dimension dimension, List<Fact> facts) {
    int missing = 0;
    for (Fact fact : facts) {
      if (!has(dimension, fact)) {
        missing++;
      }
    }
    return missing;
  }

  private static boolean has(Dimension dimension, Fact fact) {
    return switch (dimension) {
      case INVOICE_DATE -> fact.invoiceDate() != null;
      case HOSPITAL -> !fact.hospital().isBlank();
      case DEPARTMENT -> !fact.department().isBlank();
      case DIAGNOSIS -> !fact.disease().isBlank();
    };
  }
}
