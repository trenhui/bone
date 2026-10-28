package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Row;
import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Scenario;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 理算双轨一致回归（Go-Live 门禁 G2 的 CI 载体之一）。
 *
 * <p>「双轨」= 生产 `Adjustment` 聚合 vs {@link AdjustmentDualTrackFixture#REFERENCE 独立参考 oracle}。
 * 单轨单测只能证明实现自洽；两轨对撞才能发现「规格与实现同错」——这是金融理算最常见的假绿来源。
 *
 * <p>断言口径：金额一律 `isEqualByComparingTo`（`BigDecimal.equals` 会比较标度，`30.0` 与 `30.00` 会被误判为不等）。
 */
class AdjustmentDualTrackConsistencyTest {

  static Stream<Scenario> scenarios() {
    return AdjustmentDualTrackFixture.scenarios().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  @DisplayName("双轨一致：生产引擎赔款 == 参考 oracle 赔款")
  void productionMatchesReference(Scenario s) {
    BigDecimal actual = AdjustmentDualTrackFixture.production(s);
    BigDecimal expected = AdjustmentDualTrackFixture.REFERENCE.payout(s);

    assertThat(actual).as("场景 %s（%s）", s.label(), s.note()).isEqualByComparingTo(expected);
  }

  @Test
  @DisplayName("同输入同输出：重复理算金额与快照哈希均稳定（§5.2 可复算）")
  void deterministicAcrossRuns() {
    Scenario s = AdjustmentDualTrackFixture.scenarios().get(0);

    Adjustment a = adjust(s, 9001L);
    Adjustment b = adjust(s, 9002L);

    assertThat(b.getTotalAmount()).isEqualByComparingTo(a.getTotalAmount());
    assertThat(b.getFormulaSnapshot()).isEqualTo(a.getFormulaSnapshot());
    assertThat(b.getFormulaHash()).isEqualTo(a.getFormulaHash());
  }

  @Test
  @DisplayName("快照哈希可独立复算：formulaHash == sha256(formulaSnapshot)（防哈希为常量）")
  void snapshotHashIsDerivedNotConstant() throws Exception {
    for (Scenario s : AdjustmentDualTrackFixture.scenarios()) {
      Adjustment a = adjust(s, 9001L);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String recomputed =
          HexFormat.of()
              .formatHex(digest.digest(a.getFormulaSnapshot().getBytes(StandardCharsets.UTF_8)));

      assertThat(recomputed).as("场景 %s 的快照哈希须由快照内容派生", s.label()).isEqualTo(a.getFormulaHash());
    }
  }

  @Test
  @DisplayName("归因完备：每条理算至少一条明细，且明细合计 ≡ 总额（不守恒即分位丢失）")
  void attributionSumsToTotal() {
    for (Scenario s : AdjustmentDualTrackFixture.scenarios()) {
      Adjustment a = adjust(s, 9001L);

      assertThat(a.lines()).as("场景 %s 必须有归因明细", s.label()).isNotEmpty();
      BigDecimal sum =
          a.lines().stream()
              .map(AdjustmentLine::getFeePayout)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(sum).as("场景 %s 明细合计须等于总额", s.label()).isEqualByComparingTo(a.getTotalAmount());
    }
  }

  private static Adjustment adjust(Scenario s, long id) {
    List<ClaimWithItemsProjection> rows = new ArrayList<>();
    long itemId = 2001L;
    for (Row r : s.rows()) {
      ClaimWithItemsProjection p = new ClaimWithItemsProjection();
      p.setClaimId(AdjustmentDualTrackFixture.CLAIM);
      p.setClaimNo("CL001");
      p.setItemId(r.headOnly() ? null : itemId++);
      p.setItemName(r.name());
      p.setItemSocialFlag(r.socialFlag());
      p.setItemValidAmount(r.valid());
      p.setItemSelfPay1(r.selfPay1());
      rows.add(p);
    }
    AdjLiability liability =
        AdjLiability.of(
            1L,
            AdjustmentDualTrackFixture.TENANT,
            "L-DUAL",
            "v1",
            7L,
            "R01",
            "REIMBURSEMENT",
            s.payPercent(),
            s.deduct(),
            s.limit(),
            s.socialOnly() ? 1 : 0,
            0,
            "ACTIVE");
    return Adjustment.calculate(
        id,
        AdjustmentDualTrackFixture.TENANT,
        AdjustmentDualTrackFixture.CLAIM,
        rows,
        liability,
        "r1");
  }
}
