package com.bone.airag.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 轻量分词（中文可用）：字母数字串 + 中文整串 + CJK bigram（相邻汉字二元组）。
 *
 * <p><b>为什么抽出来共用</b>：BM25 词面路早已按此口径分词（{@link Bm25Scorer} 注释「中文分词修正」），
 * 而过渡嵌入 {@code TransitionalHashEmbedding} 原先仅按空白 {@code \s+} 切分——中文整句被当成单 token，向量路
 * 在中文语料上近乎失效（混合检索退化为 BM25 单路）。两路共用同一分词口径，才能让 dev/test 的「向量 + BM25」
 * 真正双路可跑，也是中文 golden-set 评测有意义的前提。
 *
 * <p>无第三方依赖（不引 jieba/lucene），符合零新依赖纪律；终局接 bge-m3 后本分词仅服务于 BM25 与过渡嵌入。
 */
public final class CjkTokenizer {

  private CjkTokenizer() {}

  /** 分词：非 {@code [a-z0-9汉]} 处切分，再对纯汉字串补相邻二元组（提升中文召回）。 */
  public static List<String> tokenize(String s) {
    if (s == null) {
      return List.of();
    }
    List<String> tokens =
        Arrays.stream(s.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+"))
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());
    List<String> withBigram = new ArrayList<>(tokens);
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
