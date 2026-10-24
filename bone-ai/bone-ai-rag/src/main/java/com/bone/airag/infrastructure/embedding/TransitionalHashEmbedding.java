package com.bone.airag.infrastructure.embedding;

import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.service.CjkTokenizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 过渡嵌入实现：基于词哈希的确定性伪向量（L2 归一化）。
 *
 * <p>仅用于本地/测试可跑通检索链路，不含语义。终局替换为 bge-m3（1024 维）等真实模型， 由消费方以 {@code @Bean EmbeddingPort} 覆盖。
 *
 * <p><b>2026-10-24 分词修正</b>：原按空白 {@code \s+} 切分，中文整句成为单 token → 向量路在中文语料上近乎失效 （混合检索退化 BM25 单路）。改为复用
 * {@link CjkTokenizer}（字母数字串 + 汉字整串 + CJK bigram），与 BM25 同口径， 使 dev/test 的双路真正可跑、并让中文 golden-set
 * 评测有意义。版本号随之升级为 {@code hash-64-cjk}（向量口径已变， 旧向量 fail-closed 跳过，避免混用不可比向量）。
 */
public class TransitionalHashEmbedding implements EmbeddingPort {

  private static final int DIM = 64;

  /** 过渡版本标识（分词口径变更 ⇒ 升级，旧向量不可比将被跳过）。 */
  private static final String VER = "hash-64-cjk";

  @Override
  public String version() {
    return VER;
  }

  @Override
  public float[] embed(String text) {
    float[] v = new float[DIM];
    if (text == null || text.isEmpty()) {
      return v;
    }
    for (String tok : CjkTokenizer.tokenize(text)) {
      int bucket = Math.floorMod(tok.hashCode(), DIM);
      v[bucket] += 1.0f;
    }
    double norm = 0;
    for (float x : v) {
      norm += x * x;
    }
    if (norm > 0) {
      double s = Math.sqrt(norm);
      for (int i = 0; i < DIM; i++) {
        v[i] = (float) (v[i] / s);
      }
    }
    return v;
  }

  /** 过渡实现：逐条循环（哈希本无批量语义）；ingest 仍走批接口以统一调用面。 */
  @Override
  public List<float[]> embedBatch(List<String> texts) {
    List<float[]> out = new ArrayList<>(texts.size());
    for (String t : texts) {
      out.add(embed(t));
    }
    return out;
  }
}
