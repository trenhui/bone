package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A6 理算聚合单测（资金核心：确定性计算 + 零赔付归因 + 快照可复算）。
 *
 * <p>金额断言一律用 {@code isEqualByComparingTo}：BigDecimal 的 equals 会比较 scale， 用错断言会让"10.0 与
 * 10.00"这种纯标度差异被误判为计算错误。
 */
class AdjustmentTest {

  private static final long TENANT = 42L;
  private static final long CLAIM = 1001L;

  /**
   * 造一条明细行（三段拆分已就绪）。
   *
   * <p><b>用 setter 而不是 38 参全构造</b>：全构造在字段增加时会静默错位（参数顺序一改，编译期不报、运行期串列）， 且每次给投影加列都要改一堆测试。setter
   * 只写本用例关心的字段，其余保持 null → 引擎按 0 处理。
   */
  private static ClaimWithItemsProjection row(
      String name, String socialFlag, BigDecimal valid, BigDecimal selfPay1) {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(CLAIM);
    p.setClaimNo("CL001");
    p.setPolicyNo("P1");
    p.setPlanId(7L);
    p.setInsuredIdHash("h");
    p.setClaimType("OUTPATIENT");
    p.setItemId(2001L);
    p.setItemName(name);
    p.setItemSocialFlag(socialFlag);
    p.setItemValidAmount(valid);
    p.setItemSelfPay1(selfPay1);
    return p;
  }

  private static AdjLiability liability(
      String payPercent, String deduct, String limit, Integer socialOnly) {
    return AdjLiability.of(
        1L,
        TENANT,
        "L-1",
        "v1",
        7L,
        "R01",
        "REIMBURSEMENT",
        new BigDecimal(payPercent),
        new BigDecimal(deduct),
        new BigDecimal(limit),
        socialOnly,
        0,
        "ACTIVE");
  }

  private static Adjustment adjust(List<ClaimWithItemsProjection> rows, AdjLiability liability) {
    return Adjustment.calculate(9001L, TENANT, CLAIM, rows, liability, "r1", Accumulation.FIRST);
  }

  @Test
  @DisplayName("主链路：基准 → 比例 → 免赔 → 限额截断（旧配置口径：免赔对象=发票费用）")
  void calculateChain() {
    // 基准 200，免赔 50，比例 90% → (200−50)×90% = 135，限额 100 → 截断为 100
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("200.00"), new BigDecimal("200.00"))),
            liability("90", "50", "100", 1));

    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("100.00");
    assertThat(adjustment.resultStatusEnum()).isEqualTo(Adjustment.ResultStatus.FROZEN);
    assertThat(adjustment.getFormulaSnapshot())
        .contains("\"payPercent\":90", "\"deduct\":50", "\"limit\":100");
  }

  @Test
  @DisplayName("未触顶：不截断，按 比例 实付")
  void noCapTruncation() {
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("200.00"), new BigDecimal("200.00"))),
            liability("90", "50", "10000", 1));

    // 旧配置（t_adj_liability 的 pay_percent/liability_deduct）的免赔对象是**发票费用**：
    // (200 − 50) × 90% = 135。V1 默认的"理算金额"口径（200×90%−50=130）由
    // LiabilityRule.simple 表达，见 AdjustmentEngineTest#deductibleTargetChangesOrder。
    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("135.00");
  }

  @Test
  @DisplayName("socialOnly：社保外费用不计入基准（R-DIR-01）")
  void socialOnlyExcludesOutOfScope() {
    Adjustment adjustment =
        adjust(
            List.of(
                row("社保内", "IN", new BigDecimal("100.00"), new BigDecimal("100.00")),
                row("社保外", "OUT", new BigDecimal("100.00"), new BigDecimal("100.00"))),
            liability("90", "0", "10000", 1));

    // 仅社保内 100 计入 → 90
    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("90.00");
  }

  @Test
  @DisplayName("免赔不得为负：免赔大于基准时按基准全额抵扣，实付 0（ZERO_DEDUCT）")
  void deductNeverNegative() {
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("30.00"), new BigDecimal("30.00"))),
            liability("90", "300", "10000", 1));

    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("0.00");
    assertThat(adjustment.lines().get(0).getZeroReason()).isEqualTo("ZERO_DEDUCT");
    assertThat(adjustment.lines().get(0).getRejectType()).isEqualTo("06");
  }

  @Test
  @DisplayName("零赔付归因：合理金额为 0 → ZERO_REASONABLE（受控码 05）")
  void zeroReasonable() {
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", BigDecimal.ZERO, BigDecimal.ZERO)),
            liability("90", "0", "10000", 1));

    assertThat(adjustment.lines().get(0).getZeroReason()).isEqualTo("ZERO_REASONABLE");
  }

  @Test
  @DisplayName("零赔付归因：比例 0 → ZERO_PERCENT")
  void zeroPercent() {
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("100.00"), new BigDecimal("100.00"))),
            liability("0", "0", "10000", 1));

    assertThat(adjustment.lines().get(0).getZeroReason()).isEqualTo("ZERO_PERCENT");
  }

  @Test
  @DisplayName("快照可复算：同输入同输出，哈希稳定（§5.2 物化不可仅版本引用）")
  void snapshotStableAndReusable() {
    AdjLiability liability = liability("90", "50", "10000", 1);
    List<ClaimWithItemsProjection> rows =
        List.of(row("诊查费", "IN", new BigDecimal("200.00"), new BigDecimal("200.00")));

    Adjustment first = adjust(rows, liability);
    Adjustment second =
        Adjustment.calculate(9002L, TENANT, CLAIM, rows, liability, "r1", Accumulation.FIRST);

    assertThat(second.getFormulaHash()).isEqualTo(first.getFormulaHash());
    assertThat(second.getTotalAmount()).isEqualByComparingTo(first.getTotalAmount());
  }

  @Test
  @DisplayName("已确认禁止重算（§5.3 硬不变量）")
  void confirmedCannotRecalculate() {
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("200.00"), new BigDecimal("200.00"))),
            liability("90", "50", "10000", 1));
    adjustment.confirm();

    assertThatThrownBy(
            () ->
                adjustment.recalculate(
                    List.of(row("诊查费", "IN", new BigDecimal("200.00"), new BigDecimal("200.00"))),
                    liability("90", "50", "10000", 1),
                    Accumulation.FIRST))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("已确认");
  }

  @Test
  @DisplayName("入参校验：明细为空 / 责任未生效 / 责任为空")
  void invariants() {
    AdjLiability active = liability("90", "0", "10000", 1);
    assertThatThrownBy(
            () ->
                Adjustment.calculate(
                    1L, TENANT, CLAIM, List.of(), active, "r1", Accumulation.FIRST))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("明细");
    assertThatThrownBy(
            () ->
                Adjustment.calculate(
                    1L,
                    TENANT,
                    CLAIM,
                    List.of(row("诊查费", "IN", BigDecimal.TEN, BigDecimal.TEN)),
                    null,
                    "r1",
                    Accumulation.FIRST))
        .isInstanceOf(DomainException.class);
    AdjLiability draft =
        AdjLiability.of(
            2L,
            TENANT,
            "L-2",
            "v1",
            7L,
            "R02",
            "REIMBURSEMENT",
            new BigDecimal("90"),
            BigDecimal.ZERO,
            new BigDecimal("10000"),
            1,
            0,
            "DRAFT");
    assertThatThrownBy(
            () ->
                Adjustment.calculate(
                    1L,
                    TENANT,
                    CLAIM,
                    List.of(row("诊查费", "IN", BigDecimal.TEN, BigDecimal.TEN)),
                    draft,
                    "r1",
                    Accumulation.FIRST))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("未生效");
  }

  @Test
  @DisplayName("精度：结果为 2 位、HALF_UP（§8.3）")
  void scaleAndRounding() {
    // 基准 33.33，免赔 0，比例 90% → 29.997 → 30.00
    Adjustment adjustment =
        adjust(
            List.of(row("诊查费", "IN", new BigDecimal("33.33"), new BigDecimal("33.33"))),
            liability("90", "0", "10000", 1));

    assertThat(adjustment.getTotalAmount()).isEqualByComparingTo("30.00");
  }
}
