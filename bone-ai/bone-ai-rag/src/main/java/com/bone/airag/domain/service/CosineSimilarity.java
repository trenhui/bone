package com.bone.airag.domain.service;

/** 余弦相似度（应用层向量检索，过渡形态）。 */
public final class CosineSimilarity {

  private CosineSimilarity() {}

  public static double of(float[] a, float[] b) {
    if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
      return 0.0;
    }
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    if (na == 0 || nb == 0) {
      return 0.0;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }
}
