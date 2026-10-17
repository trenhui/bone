package com.bone.airag.infrastructure.embedding;

import com.bone.airag.domain.gateway.EmbeddingPort;

/**
 * 过渡嵌入实现：基于词哈希的确定性伪向量（L2 归一化）。
 *
 * <p>仅用于本地/测试可跑通检索链路，不含语义。终局替换为 bge-m3（1024 维）等真实模型， 由消费方以 {@code @Bean EmbeddingPort} 覆盖。
 */
public class TransitionalHashEmbedding implements EmbeddingPort {

  private static final int DIM = 64;

  /** 过渡版本标识（真实模型接入时变更为语义模型版本，如 bge-m3@v1）。 */
  private static final String VER = "hash-64";

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
    for (String tok : text.toLowerCase().split("\\s+")) {
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
}
