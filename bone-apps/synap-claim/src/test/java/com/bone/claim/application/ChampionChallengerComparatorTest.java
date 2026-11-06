package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** champion / challenger 逐案离线对比：缺金标只产差异清单，不产优劣结论。 */
class ChampionChallengerComparatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("金标齐备：逐案 agree 判定 + 一致率汇总结论")
  void withGold() {
    String champion =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"REVIEW\"}]";
    String challenger =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"REVIEW\",\"goldDecision\":\"REVIEW\"}]";

    var c = ChampionChallengerComparator.compare(champion, challenger, MAPPER);

    assertThat(c.totalCases()).isEqualTo(2);
    assertThat(c.missingGold()).isZero();
    assertThat(c.comparedCases()).isEqualTo(2);
    assertThat(c.disagreeCount()).isEqualTo(1);
    assertThat(c.diffs())
        .anySatisfy(
            d -> {
              assertThat(d.caseKey()).isEqualTo("C-2");
              assertThat(d.reason()).isEqualTo("DISAGREE");
            });
    assertThat(c.conclusion()).contains("50.00%");
  }

  @Test
  @DisplayName("缺金标：差异清单逐案给 NO_GOLD，conclusion=null 不产优劣结论")
  void missingGold() {
    String champion =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"APPROVE\"}]";
    String challenger =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"REVIEW\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"APPROVE\"}]";

    var c = ChampionChallengerComparator.compare(champion, challenger, MAPPER);

    assertThat(c.missingGold()).isEqualTo(1);
    assertThat(c.comparedCases()).isEqualTo(1);
    assertThat(c.disagreeCount()).isEqualTo(1);
    assertThat(c.conclusion()).isNull();
    assertThat(c.reason()).isEqualTo("GOLD_MISSING");
    assertThat(c.diffs())
        .anySatisfy(
            d -> {
              assertThat(d.caseKey()).isEqualTo("C-2");
              assertThat(d.reason()).isEqualTo("NO_GOLD");
            });
  }

  @Test
  @DisplayName("坏 JSON / 两侧案件键不对齐：不猜不编，如实计数")
  void malformedAndUnaligned() {
    var bad = ChampionChallengerComparator.compare("not-json", "[]", MAPPER);
    assertThat(bad.reason()).isEqualTo("CHAMPION_UNPARSEABLE");
    assertThat(bad.totalCases()).isZero();

    var unaligned =
        ChampionChallengerComparator.compare(
            "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"}]",
            "[]",
            MAPPER);
    assertThat(unaligned.reason()).isEqualTo("CASE_KEY_MISMATCH");
    assertThat(unaligned.comparedCases()).isZero();
  }
}
