package com.bone.claim.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * champion / challenger 逐案离线对比（纯函数，冻结集口径）。
 *
 * <p>输入为 champion 快照（冻结集 eval/ai-decision-evalset.json 的导出形状）与 challenger 运行产物 两段 JSON 数组（元素含
 * caseKey / decisionResult / 可选 goldDecision）。逐案对比 + 汇总一致率；
 * <b>缺金标只产差异清单（reason=NO_GOLD），绝不产优劣结论</b>——金标不齐时任何「challenger 更优」 的说法都是把偶然当规律。晋升判定走域内核
 * challengerAdmissible 全链判据，本类不做。
 */
public final class ChampionChallengerComparator {

  private ChampionChallengerComparator() {}

  /** 单案差异：goldPresent=false 时 agree 无意义（一律 false），reason=NO_GOLD。 */
  public record CaseDiff(String caseKey, boolean goldPresent, boolean agree, String reason) {}

  /**
   * 对比汇总：conclusion 仅在金标齐备时给一致率文本；缺金标/键不对齐/坏 JSON 时为 null， reason 给受控成因码（GOLD_MISSING /
   * CASE_KEY_MISMATCH / CHAMPION_UNPARSEABLE / CHALLENGER_UNPARSEABLE）。
   */
  public record Comparison(
      int totalCases,
      int comparedCases,
      int missingGold,
      int disagreeCount,
      List<CaseDiff> diffs,
      String conclusion,
      String reason) {

    public Comparison {
      diffs = List.copyOf(diffs);
    }
  }

  public static Comparison compare(
      String championJson, String challengerJson, ObjectMapper mapper) {
    JsonNode champion = parse(mapper, championJson);
    if (champion == null) {
      return empty("CHAMPION_UNPARSEABLE");
    }
    JsonNode challenger = parse(mapper, challengerJson);
    if (challenger == null) {
      return empty("CHALLENGER_UNPARSEABLE");
    }
    Map<String, JsonNode> byKey = new LinkedHashMap<>();
    for (JsonNode n : champion) {
      byKey.put(n.path("caseKey").asText(""), n);
    }
    Map<String, JsonNode> challengerByKey = new LinkedHashMap<>();
    for (JsonNode n : challenger) {
      challengerByKey.put(n.path("caseKey").asText(""), n);
    }
    if (!byKey.keySet().equals(challengerByKey.keySet())) {
      return empty("CASE_KEY_MISMATCH");
    }

    List<CaseDiff> diffs = new ArrayList<>();
    int missingGold = 0;
    int disagree = 0;
    for (Map.Entry<String, JsonNode> e : byKey.entrySet()) {
      String gold = e.getValue().path("goldDecision").asText("");
      if (gold.isBlank()) {
        missingGold++;
        diffs.add(new CaseDiff(e.getKey(), false, false, "NO_GOLD"));
        continue;
      }
      boolean agree =
          e.getValue()
              .path("decisionResult")
              .asText("")
              .equalsIgnoreCase(challengerByKey.get(e.getKey()).path("decisionResult").asText(""));
      if (!agree) {
        disagree++;
      }
      diffs.add(new CaseDiff(e.getKey(), true, agree, agree ? "AGREE" : "DISAGREE"));
    }
    int compared = byKey.size() - missingGold;
    if (missingGold > 0 || compared == 0) {
      return new Comparison(
          byKey.size(), compared, missingGold, disagree, diffs, null, "GOLD_MISSING");
    }
    double agreeRate = (byKey.size() - (double) disagree) / byKey.size() * 100;
    return new Comparison(
        byKey.size(),
        compared,
        0,
        disagree,
        diffs,
        "compared="
            + compared
            + ", agreeRate="
            + String.format(java.util.Locale.ROOT, "%.2f", agreeRate)
            + "%（仅汇总，优劣结论须走 challengerAdmissible 全链判据）",
        "OK");
  }

  private static JsonNode parse(ObjectMapper mapper, String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode node = mapper.readTree(json);
      return node.isArray() ? node : null;
    } catch (Exception ex) {
      return null;
    }
  }

  private static Comparison empty(String reason) {
    return new Comparison(0, 0, 0, 0, List.of(), null, reason);
  }
}
