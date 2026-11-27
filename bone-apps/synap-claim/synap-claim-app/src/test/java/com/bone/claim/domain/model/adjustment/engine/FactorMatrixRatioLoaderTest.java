package com.bone.claim.domain.model.adjustment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.liability.FactorMatrix;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 因子矩阵装载器的判别力：零行为变更 / 受控解析 / fail-closed / 重叠拒绝。 */
class FactorMatrixRatioLoaderTest {

  private static final BigDecimal PAY_PERCENT = new BigDecimal("80");

  private FactorMatrix row(String rowKey, String value) {
    return FactorMatrix.of(
        (long) rowKey.hashCode(),
        1L,
        10L,
        "LB-UUID",
        "MIXED",
        rowKey,
        FactorMatrix.DEFAULT_COL_KEY,
        new BigDecimal(value),
        1);
  }

  @Test
  @DisplayName("空矩阵 → uniform(payPercent)，与引入前逐字一致（零行为变更）")
  void emptyRows_fallsBackToUniform() {
    PayRatioTable table = FactorMatrixRatioLoader.load(List.of(), PAY_PERCENT, "LB");

    assertThat(table.form()).isEqualTo(PayRatioTable.FORM_UNIFORM);
    assertThat(
            table
                .lookup(PayRatioTable.RatioQuery.of(FeePools.Bucket.REASONABLE, BigDecimal.ZERO))
                .percent())
        .isEqualByComparingTo(PAY_PERCENT);
  }

  @Test
  @DisplayName("null 行集合同样回落 uniform（防御性）")
  void nullRows_fallsBackToUniform() {
    PayRatioTable table = FactorMatrixRatioLoader.load(null, PAY_PERCENT, "LB");
    assertThat(table.form()).isEqualTo(PayRatioTable.FORM_UNIFORM);
  }

  @Test
  @DisplayName("医院等级二维矩阵：命中唯一格，返回该格比例")
  void multiDim_hitsRightCell() {
    PayRatioTable table =
        FactorMatrixRatioLoader.load(
            List.of(row("HOSPITAL_LEVEL=一级", "90"), row("HOSPITAL_LEVEL=二级", "70")),
            PAY_PERCENT,
            "LB");

    assertThat(table.form()).isEqualTo(PayRatioTable.FORM_MULTI);
    PayRatioTable.RatioQuery q1 =
        new PayRatioTable.RatioQuery(null, null, BigDecimal.ZERO, BigDecimal.ZERO, "一级", null);
    PayRatioTable.RatioQuery q2 =
        new PayRatioTable.RatioQuery(null, null, BigDecimal.ZERO, BigDecimal.ZERO, "二级", null);
    assertThat(table.lookup(q1).percent()).isEqualByComparingTo(new BigDecimal("90"));
    assertThat(table.lookup(q2).percent()).isEqualByComparingTo(new BigDecimal("70"));
  }

  @Test
  @DisplayName("累计区间 + 医保标志复合因子：区间左闭右开命中正确档")
  void rangeAndMedical_hitsRightCell() {
    PayRatioTable table =
        FactorMatrixRatioLoader.load(
            List.of(
                row("ACCUMULATED=[0,1000);MEDICAL_INSURANCE=社保", "90"),
                row("ACCUMULATED=[1000,);MEDICAL_INSURANCE=社保", "60")),
            PAY_PERCENT,
            "LB");

    PayRatioTable.RatioQuery low =
        new PayRatioTable.RatioQuery(
            null, true, new BigDecimal("500"), BigDecimal.ZERO, null, null);
    PayRatioTable.RatioQuery high =
        new PayRatioTable.RatioQuery(
            null, true, new BigDecimal("1500"), BigDecimal.ZERO, null, null);
    assertThat(table.lookup(low).percent()).isEqualByComparingTo(new BigDecimal("90"));
    assertThat(table.lookup(high).percent()).isEqualByComparingTo(new BigDecimal("60"));
  }

  @Test
  @DisplayName("未知因子 / 非法枚举值 / 非法区间 / 重复因子 → fail-closed（RuleGap）")
  void invalidRowKey_failsClosed() {
    assertGap(List.of(row("UNKNOWN_FACTOR=x", "90")));
    assertGap(List.of(row("BUCKET=NOT_A_BUCKET", "90")));
    assertGap(List.of(row("MEDICAL_INSURANCE=也许", "90")));
    assertGap(List.of(row("ACCUMULATED=[1000,500)", "90")));
    assertGap(List.of(row("HOSPITAL_LEVEL=一级;HOSPITAL_LEVEL=二级", "90")));
    assertGap(List.of(row("HOSPITAL_LEVEL=", "90")));
    assertGap(List.of(row("no-equals-sign", "90")));
  }

  @Test
  @DisplayName("区间重叠的两格 → 装载期拒绝（不留到理赔期随机挑格）")
  void overlappingRanges_rejectedAtLoad() {
    assertThatThrownBy(
            () ->
                FactorMatrixRatioLoader.load(
                    List.of(row("ACCUMULATED=[0,1000)", "90"), row("ACCUMULATED=[500,1500)", "70")),
                    PAY_PERCENT,
                    "LB"))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("重叠");
  }

  @Test
  @DisplayName("负格值 → fail-closed（比例不得为负）")
  void negativeCellValue_failsClosed() {
    assertGap(List.of(row("HOSPITAL_LEVEL=一级", "-5")));
  }

  private static void assertGap(List<FactorMatrix> rows) {
    assertThatThrownBy(() -> FactorMatrixRatioLoader.load(rows, PAY_PERCENT, "LB"))
        .isInstanceOf(RuleGap.class);
  }
}
