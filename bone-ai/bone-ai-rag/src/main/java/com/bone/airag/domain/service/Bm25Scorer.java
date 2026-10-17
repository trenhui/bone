package com.bone.airag.domain.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BM25 评分（词面检索路，与向量路 RRF 融合）。
 *
 * <p><b>性能修正（2026-10-17 复核修复）</b>：原 {@code score()} 对<b>每次调用</b>都重算全语料 {@code avgdl/df} ——{@code
 * RagApplicationService.retrieve} 对每个 chunk 调一次，退化为 O(N²) 分词，万级切片即超 P95 阈值。 改为「每查询预计算一次 {@link
 * CorpusStats} 传入」，复杂度降到 O(N)。
 *
 * <p><b>中文分词修正</b>：仅按连续汉字整串匹配召回极低，补 CJK bigram（相近字二元组），对齐 RAG 中文检索基线。
 */
public final class Bm25Scorer {

  private static final double K1 = 1.5;
  private static final double B = 0.75;

  private Bm25Scorer() {}

  /** 一次查询的语料统计（全查询只算一次）。 */
  public static final class CorpusStats {
    final double avgdl;
    final Map<String, Integer> df;
    final int n;

    private CorpusStats(double avgdl, Map<String, Integer> df, int n) {
      this.avgdl = avgdl;
      this.df = df;
      this.n = n;
    }

    public static CorpusStats of(List<String> corpusTexts) {
      double avgdl = corpusTexts.stream().mapToInt(t -> tokenize(t).size()).average().orElse(1.0);
      Map<String, Integer> df = new HashMap<>();
      for (String ct : corpusTexts) {
        for (String t : new HashSet<>(tokenize(ct))) {
          df.merge(t, 1, Integer::sum);
        }
      }
      return new CorpusStats(avgdl, df, corpusTexts.size());
    }
  }

  public static double score(String query, String docText, CorpusStats stats) {
    List<String> q = tokenize(query);
    if (q.isEmpty()) {
      return 0.0;
    }
    List<String> docTokens = tokenize(docText);
    int docLen = docTokens.size();
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
      int dfTerm = stats.df.getOrDefault(term, 0);
      double idf = Math.log((stats.n - dfTerm + 0.5) / (dfTerm + 0.5) + 1.0);
      sum += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * docLen / Math.max(stats.avgdl, 1.0)));
    }
    return sum;
  }

  /** 分词：字母数字串 + 中文整字 + CJK bigram（提升中文召回）。 */
  static List<String> tokenize(String s) {
    if (s == null) {
      return List.of();
    }
    List<String> tokens =
        Arrays.stream(s.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+"))
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());
    // 中文 bigram：把相邻汉字两两成组，缓解「整串匹配」召回过低
    List<String> withBigram = new java.util.ArrayList<>(tokens);
    for (String t : tokens) {
      if (t.length() > 1 && t.chars().allMatch(c -> c >= 0x4e00 && c <= 0x9fa5)) {
        for (int i = 0; i + 1 < t.length(); i++) {
          withBigram.add(t.substring(i, i + 2));
        }
      }
    }
    return withBigram;
  }
}
