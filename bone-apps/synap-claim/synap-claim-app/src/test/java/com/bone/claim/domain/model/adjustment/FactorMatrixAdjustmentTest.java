package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.adjustment.engine.FactorMatrixRatioLoader;
import com.bone.claim.domain.model.adjustment.engine.PayRatioTable;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.FactorMatrix;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 因子矩阵真正进引擎算钱的装配级判别力（接线是否生效的唯一实证）。
 *
 * <p>单测装载器只证明"矩阵→PayRatioTable"转换正确；本测试走完整 {@link Adjustment#calculate} 装配链， 证明 {@code
 * AdjLiability.ratioOverride} 挂上后，赔付金额确实随矩阵格值变化——删掉 {@code Adjustment.apply()} 里那行 {@code
 * .withRatios(...)}，第二个用例立刻转红。这是对"配了不生效"的正面击杀。
 */
class FactorMatrixAdjustmentTest {

  private static final long TENANT = 1L;
  private static final long CLAIM = 9001L;

  private static List<ClaimWithItemsProjection> rows() {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(CLAIM);
    p.setClaimNo("CL-MATRIX");
    p.setItemId(2001L);
    p.setItemName("门诊诊查费");
    p.setItemSocialFlag("IN");
    p.setItemValidAmount(new BigDecimal("1000"));
    p.setItemSelfPay1(BigDecimal.ZERO);
    return List.of(p);
  }

  private static AdjLiability liability() {
    return AdjLiability.of(
        1L,
        TENANT,
        "L-MATRIX",
        "v1",
        7L,
        "R01",
        "REIMBURSEMENT",
        new BigDecimal("80"),
        BigDecimal.ZERO,
        new BigDecimal("10000"),
        0,
        0,
        "ACTIVE");
  }

  @Test
  @DisplayName("未挂矩阵 → 按责任统一比例 80% 赔付（基线）")
  void withoutMatrix_usesUniformPayPercent() {
    Adjustment a =
        Adjustment.calculate(9001L, TENANT, CLAIM, rows(), liability(), "r1", Accumulation.FIRST);

    assertThat(a.getTotalAmount()).isEqualByComparingTo(new BigDecimal("800.00"));
  }

  @Test
  @DisplayName("挂 95% 矩阵 → 赔付金额随矩阵变化（证明矩阵真进引擎，不是配了不生效）")
  void withMatrix_changesPayout() {
    AdjLiability liability = liability();
    // 用 ACCUMULATED=[0,) 无上界区间：引擎必传累计额，格必命中，金额确定按 95% 算。
    FactorMatrix matrix =
        FactorMatrix.of(
            1L,
            TENANT,
            1L,
            "L-MATRIX",
            "ACCUMULATED",
            "ACCUMULATED=[0,)",
            FactorMatrix.DEFAULT_COL_KEY,
            new BigDecimal("95"),
            1);
    matrix.publish();
    PayRatioTable ratios =
        FactorMatrixRatioLoader.load(List.of(matrix), new BigDecimal("80"), "L-MATRIX");
    liability.attachRatios(ratios);

    Adjustment a =
        Adjustment.calculate(9002L, TENANT, CLAIM, rows(), liability, "r1", Accumulation.FIRST);

    assertThat(a.getTotalAmount()).isEqualByComparingTo(new BigDecimal("950.00"));
  }
}
