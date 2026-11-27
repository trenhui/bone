package com.bone.claim.domain.model.adjustment.engine;

import com.bone.claim.domain.model.liability.FactorMatrix;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 因子矩阵 → {@link PayRatioTable} 的唯一装载器（费率矩阵进引擎的受控入口）。
 *
 * <p><b>为什么单独成类</b>：矩阵的 {@code rowKey} 是 {@code FACTOR=value;FACTOR=value} 自由文本，而 {@link
 * PayRatioTable.RatioSelector} 是强类型（枚举 / 区间 / 布尔）。二者的映射是"配错就赔错钱"的高危转换——必须独立、纯函数、可判别力单测，且未知因子/非法值一律
 * {@link RuleGap} fail-closed，绝不猜一个维度。
 *
 * <p><b>零行为变更承诺</b>：矩阵行集合为空（历史责任从未配置矩阵）时返回 {@code uniform(payPercent)}，与引入前装配结果逐字一致；
 * 只有真正配了矩阵行才切换成多维表。
 *
 * <p><b>口径（已拍板，沿用 LiabilityRule 类别优先先例）</b>：矩阵格优先；未配置的维度在 selector 中为 null（=不限，等价于继承统一比例在该维度的行为）；
 * 某维度配了但查询时该维度无值（如医疗标志未知），按"不限"的格命中——这是 PayRatioTable 既有语义，不在此另立规则。
 *
 * <p><b>格值语义</b>：{@code cell_value} 在此<b>只作赔付比例（百分数）</b>，与 {@code generateFormula} 既有用法一致。
 * 免赔/限额的矩阵化需要 DDL 增加"格值类型"列区分，属后续 change，本装载器遇到非比例语义的 dimension_set 不做臆测。
 */
public final class FactorMatrixRatioLoader {

  /** 受控因子标识（与 diagnose 文档 §8.2 工程落点一致；未知因子 fail-closed）。 */
  private static final Set<String> FACTOR_BUCKET = Set.of("BUCKET");

  private static final Set<String> FACTOR_MEDICAL = Set.of("MEDICAL_INSURANCE");
  private static final Set<String> FACTOR_ACCUMULATED = Set.of("ACCUMULATED");
  private static final Set<String> FACTOR_INVOICE_TOTAL = Set.of("INVOICE_TOTAL");
  private static final Set<String> FACTOR_HOSPITAL_LEVEL = Set.of("HOSPITAL_LEVEL");
  private static final Set<String> FACTOR_DISEASE_TYPE = Set.of("DISEASE_TYPE");

  private static final Set<String> ALL_FACTORS =
      Set.of(
          "BUCKET",
          "MEDICAL_INSURANCE",
          "ACCUMULATED",
          "INVOICE_TOTAL",
          "HOSPITAL_LEVEL",
          "DISEASE_TYPE");

  private FactorMatrixRatioLoader() {}

  /**
   * 装载赔付比例表。
   *
   * @param rows 当前生效版本的矩阵行（空 = 未配置）
   * @param payPercent 责任统一比例（百分数），仅在无矩阵时用于 uniform
   * @param liabilityLabel 责任标识（写进格 label，便于快照定位来源）
   */
  public static PayRatioTable load(
      List<FactorMatrix> rows, BigDecimal payPercent, String liabilityLabel) {
    if (rows == null || rows.isEmpty()) {
      return PayRatioTable.uniform(payPercent);
    }
    List<PayRatioTable.Entry> entries = new ArrayList<>(rows.size());
    for (FactorMatrix row : rows) {
      entries.add(toEntry(row, liabilityLabel));
    }
    PayRatioTable table = PayRatioTable.of(entries);
    // 装载期自检：区间重叠/重复因子组合在配置期就暴露，不留到理赔期随机挑格。
    table.validate();
    return table;
  }

  private static PayRatioTable.Entry toEntry(FactorMatrix row, String liabilityLabel) {
    BigDecimal percent = row.getCellValue();
    if (percent == null || percent.signum() < 0) {
      throw RuleGap.of(
          RuleGap.Code.RATIO_NOT_MATCHED,
          "矩阵格值非法（赔付比例须为非负百分数）: liability=%s row=%s value=%s",
          liabilityLabel,
          row.getRowKey(),
          percent);
    }
    PayRatioTable.RatioSelector selector = parseRowKey(row.getRowKey(), liabilityLabel);
    String label = liabilityLabel + " / " + row.getRowKey();
    return new PayRatioTable.Entry(label, selector, percent, null);
  }

  /** 解析 {@code FACTOR=value;...} 行键为强类型 selector；未知因子/非法值/重复因子一律 fail-closed。 */
  static PayRatioTable.RatioSelector parseRowKey(String rowKey, String liabilityLabel) {
    if (rowKey == null || rowKey.isBlank()) {
      throw RuleGap.of(RuleGap.Code.RATIO_NOT_MATCHED, "矩阵行键为空: liability=%s", liabilityLabel);
    }
    FeePools.Bucket bucket = null;
    Boolean medical = null;
    AmountRange accumulated = null;
    AmountRange invoiceTotal = null;
    String hospitalLevel = null;
    String diseaseType = null;
    Set<String> seen = new LinkedHashSet<>();

    for (String part : rowKey.split(";", -1)) {
      int eq = part.indexOf('=');
      if (eq <= 0) {
        throw RuleGap.of(
            RuleGap.Code.RATIO_NOT_MATCHED,
            "矩阵行键段非法（应为 FACTOR=value）: liability=%s row=%s seg=%s",
            liabilityLabel,
            rowKey,
            part);
      }
      String factor = part.substring(0, eq).trim();
      String value = part.substring(eq + 1).trim();
      if (!ALL_FACTORS.contains(factor)) {
        throw RuleGap.of(
            RuleGap.Code.RATIO_NOT_MATCHED,
            "未知矩阵因子（无法映射为赔付比例维度）: liability=%s row=%s factor=%s",
            liabilityLabel,
            rowKey,
            factor);
      }
      if (!seen.add(factor)) {
        throw RuleGap.of(
            RuleGap.Code.RATIO_NOT_MATCHED,
            "矩阵行键重复因子: liability=%s row=%s factor=%s",
            liabilityLabel,
            rowKey,
            factor);
      }
      if (value.isEmpty()) {
        throw RuleGap.of(
            RuleGap.Code.RATIO_NOT_MATCHED,
            "矩阵因子值为空: liability=%s row=%s factor=%s",
            liabilityLabel,
            rowKey,
            factor);
      }
      if (FACTOR_BUCKET.contains(factor)) {
        bucket = parseBucket(value, liabilityLabel, rowKey);
      } else if (FACTOR_MEDICAL.contains(factor)) {
        medical = parseMedical(value, liabilityLabel, rowKey);
      } else if (FACTOR_ACCUMULATED.contains(factor)) {
        accumulated = parseRange(value, liabilityLabel, rowKey);
      } else if (FACTOR_INVOICE_TOTAL.contains(factor)) {
        invoiceTotal = parseRange(value, liabilityLabel, rowKey);
      } else if (FACTOR_HOSPITAL_LEVEL.contains(factor)) {
        hospitalLevel = value;
      } else if (FACTOR_DISEASE_TYPE.contains(factor)) {
        diseaseType = value;
      }
    }
    return new PayRatioTable.RatioSelector(
        bucket, medical, accumulated, invoiceTotal, hospitalLevel, diseaseType);
  }

  private static FeePools.Bucket parseBucket(String value, String liabilityLabel, String rowKey) {
    try {
      return FeePools.Bucket.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw RuleGap.of(
          RuleGap.Code.RATIO_NOT_MATCHED,
          "矩阵 BUCKET 因子非法（应为 %s 之一）: liability=%s row=%s value=%s",
          java.util.Arrays.toString(FeePools.Bucket.values()),
          liabilityLabel,
          rowKey,
          value);
    }
  }

  /** 受控医保值：社保/医保 → true；自费/非社保 → false。其它值 fail-closed，不猜。 */
  private static Boolean parseMedical(String value, String liabilityLabel, String rowKey) {
    return switch (value) {
      case "社保", "医保", "SHARE", "TRUE", "true" -> Boolean.TRUE;
      case "自费", "非社保", "SELF", "FALSE", "false" -> Boolean.FALSE;
      default -> throw RuleGap.of(
          RuleGap.Code.RATIO_NOT_MATCHED,
          "矩阵 MEDICAL_INSURANCE 因子非法（社保/医保 或 自费/非社保）: liability=%s row=%s value=%s",
          liabilityLabel,
          rowKey,
          value);
    };
  }

  /** 区间语法：{@code [from,to)}（含 {@code [from,+} 无上界）或 {@code from+}；非法格式 fail-closed。 */
  private static AmountRange parseRange(String value, String liabilityLabel, String rowKey) {
    try {
      // 方括号格式必须先判：'[1000,+' 同样以 '+' 结尾，若先走 from+ 分支会切出 '[1000' 导致 NumberFormatException。
      if (value.startsWith("[") && value.endsWith(")")) {
        String inner = value.substring(1, value.length() - 1);
        String[] parts = inner.split(",", -1);
        if (parts.length != 2) {
          throw new IllegalArgumentException("区间须为 [from,to)");
        }
        String to = parts[1].trim();
        return AmountRange.of(parts[0].trim(), to.isEmpty() ? null : to);
      }
      if (value.endsWith("+")) {
        return AmountRange.atLeast(value.substring(0, value.length() - 1).trim());
      }
      throw new IllegalArgumentException("区间须为 [from,to) 或 from+");
    } catch (RuntimeException ex) {
      throw RuleGap.wrap(
          RuleGap.Code.RATIO_NOT_MATCHED,
          "矩阵区间因子非法: liability=%s row=%s value=%s (%s)"
              .formatted(liabilityLabel, rowKey, value, ex.getMessage()),
          ex);
    }
  }
}
