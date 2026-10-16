package com.bone.airag.domain.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** BM25 评分（词面检索路，与向量路 RRF 融合）。 */
public final class Bm25Scorer {

  private static final double K1 = 1.5;
  private static final double B = 0.75;

  private Bm25Scorer() {}

  public static double score(String query, String docText, List<String> corpusTexts) {
    List<String> q = tokenize(query);
    if (q.isEmpty()) {
      return 0.0;
    }
    List<String> docTokens = tokenize(docText);
    int docLen = docTokens.size();
    double avgdl = corpusTexts.stream().mapToInt(t -> tokenize(t).size()).average().orElse(1.0);
    Map<String, Integer> df = new HashMap<>();
    for (String ct : corpusTexts) {
      Set<String> uniq = new HashSet<>(tokenize(ct));
      for (String t : uniq) {
        df.merge(t, 1, Integer::sum);
      }
    }
    int n = corpusTexts.size();
    double sum = 0;
    for (String term : q) {
      int f = 0;
      for (String dt : docTokens) {
        if (dt.equals(term)) {
          f++;
        }
      }
      if (f == 0) {
        continue;
      }
      int dfTerm = df.getOrDefault(term, 0);
      double idf = Math.log((n - dfTerm + 0.5) / (dfTerm + 0.5) + 1.0);
      sum += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * docLen / Math.max(avgdl, 1.0)));
    }
    return sum;
  }

  static List<String> tokenize(String s) {
    if (s == null) {
      return List.of();
    }
    return Arrays.stream(s.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+"))
        .filter(t -> !t.isEmpty())
        .collect(Collectors.toList());
  }
}
