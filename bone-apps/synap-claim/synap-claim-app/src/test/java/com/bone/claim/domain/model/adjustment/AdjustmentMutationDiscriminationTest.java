package com.bone.claim.domain.model.adjustment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Oracle;
import com.bone.claim.domain.model.adjustment.AdjustmentDualTrackFixture.Scenario;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 理算测试套件的「判别力自证」（Go-Live 门禁 G2 的防假绿证据）。
 *
 * <p><b>为什么必须有本类</b>：双轨一致只能证明「两轨都没错」。若比对口径失效（例如一律取绝对值、只比整数位、
 * 异常吞掉当通过），双轨测试会**恒定全绿**而毫无保护力——这正是本仓反复强调的「假绿」。本类用三层证据反证：
 *
 * <ol>
 *   <li><b>变异必被识别</b>：19 份「被改坏的 oracle」每一份都必须在≥1 个场景与生产实现分歧；
 *   <li><b>正确 oracle 零误报</b>：正确 oracle 在全部场景与生产实现分歧数为 0（否则比对在乱报，红也无意义）；
 *   <li><b>比对器灵敏度负向对照</b>：人为把正确结果扰动 0.01 必须被识别——证明「全部相等」不是比对器失明导致的。
 * </ol>
 *
 * <p>任一变异「零场景可辨」即说明该类缺陷可悄悄穿过整套回归；任一层证据缺失，本类自身即不成立。
 */
class AdjustmentMutationDiscriminationTest {

  @Test
  @DisplayName("19 类变异全部被识别（零场景可辨 = 该缺陷可静默穿过回归）")
  void everyMutantIsDetected() {
    List<Scenario> scenarios = AdjustmentDualTrackFixture.scenarios();
    List<String> blindSpots = new ArrayList<>();
    StringBuilder evidence = new StringBuilder();

    for (Map.Entry<String, Oracle> e : AdjustmentDualTrackFixture.mutants().entrySet()) {
      String mutant = e.getKey();
      List<String> caughtBy = new ArrayList<>();
      for (Scenario s : scenarios) {
        if (AdjustmentDualTrackFixture.production(s).compareTo(e.getValue().payout(s)) != 0) {
          caughtBy.add(s.label());
        }
      }
      if (caughtBy.isEmpty()) {
        blindSpots.add(mutant);
      } else {
        evidence.append(mutant).append(" ← ").append(caughtBy.get(0)).append("；");
      }
    }

    assertThat(blindSpots).as("以下变异零场景可辨，说明回归网存在盲区，必须补场景：%s", blindSpots).isEmpty();
    assertThat(evidence.toString()).as("判别力证据不得为空（否则 19 个变异根本没跑）").isNotEmpty();
  }

  @Test
  @DisplayName("正确 oracle 零误报：与生产实现无任何分歧")
  void referenceHasNoFalsePositive() {
    for (Scenario s : AdjustmentDualTrackFixture.scenarios()) {
      assertThat(AdjustmentDualTrackFixture.production(s))
          .as("正确 oracle 在场景 %s 上不应与生产实现分歧", s.label())
          .isEqualByComparingTo(AdjustmentDualTrackFixture.REFERENCE.payout(s));
    }
  }

  @Test
  @DisplayName("比对器灵敏度：扰动 0.01 必须被识别（防比对器失明导致的全绿）")
  void comparatorDetectsOneCentDrift() {
    Oracle driftedByOneCent =
        new Oracle() {
          @Override
          public String name() {
            return "DRIFT_0.01";
          }

          @Override
          public BigDecimal payout(Scenario s) {
            return AdjustmentDualTrackFixture.REFERENCE.payout(s).add(new BigDecimal("0.01"));
          }
        };

    long detected =
        AdjustmentDualTrackFixture.scenarios().stream()
            .filter(
                s ->
                    AdjustmentDualTrackFixture.production(s).compareTo(driftedByOneCent.payout(s))
                        != 0)
            .count();

    assertThat(detected)
        .as("一元钱以内的偏差若不可辨，双轨比对即失去意义")
        .isEqualTo(AdjustmentDualTrackFixture.scenarios().size());
  }

  @Test
  @DisplayName("变异清单固定为 19 项：删变异 = 削弱证据，必须显式可见")
  void mutantInventoryIsPinned() {
    assertThat(AdjustmentDualTrackFixture.mutants()).hasSize(19);
    assertThat(AdjustmentDualTrackFixture.scenarios())
        .as("场景数须覆盖全部变异的可辨性")
        .hasSizeGreaterThanOrEqualTo(20);
  }
}
