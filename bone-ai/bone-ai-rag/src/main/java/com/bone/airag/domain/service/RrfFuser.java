package com.bone.airag.domain.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** RRF（Reciprocal Rank Fusion）融合：两路排序按各自名次倒数加权合并。 */
public final class RrfFuser {

  private RrfFuser() {}

  public static Map<String, Double> fuse(
      Map<String, Double> vectorScores,
      Map<String, Double> bm25Scores,
      int k,
      double wVector,
      double wBm25) {
    List<String> vRank = rankByScore(vectorScores);
    List<String> bRank = rankByScore(bm25Scores);
    Map<String, Integer> vPos = position(vRank);
    Map<String, Integer> bPos = position(bRank);
    Set<String> keys = new HashSet<>();
    keys.addAll(vectorScores.keySet());
    keys.addAll(bm25Scores.keySet());

    Map<String, Double> fused = new LinkedHashMap<>();
    for (String key : keys) {
      // 某路无数据（空列表）时该路贡献 0；非空列表里缺失的键按“末位之后”计（最弱）
      int rv = vectorScores.isEmpty() ? Integer.MAX_VALUE : vPos.getOrDefault(key, vRank.size());
      int rb = bm25Scores.isEmpty() ? Integer.MAX_VALUE : bPos.getOrDefault(key, bRank.size());
      double s = wVector / (k + rv) + wBm25 / (k + rb);
      fused.put(key, s);
    }
    return fused.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
  }

  private static List<String> rankByScore(Map<String, Double> scores) {
    return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .map(Map.Entry::getKey)
        .toList();
  }

  private static Map<String, Integer> position(List<String> ranked) {
    Map<String, Integer> pos = new HashMap<>();
    for (int i = 0; i < ranked.size(); i++) {
      pos.put(ranked.get(i), i);
    }
    return pos;
  }
}
