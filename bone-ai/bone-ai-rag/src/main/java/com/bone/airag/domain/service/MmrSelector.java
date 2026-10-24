package com.bone.airag.domain.service;

import java.util.ArrayList;
import java.util.List;

/**
 * MMR（Maximal Marginal Relevance）多样性选取（详设 §2.6 P1-9，2026-10-23 落地）。
 *
 * <p>选取式而非重排式：从精排后的候选里迭代挑 topK，每步选 {@code λ·相关性 − (1−λ)·与已选集的最大相似度} 最大者——防 top-K 全来自同一条款 / 同一章节
 * （引用集中、视角单一）。相似度用切片各自入库时写入的嵌入向量（仅当与当前嵌入模型同版本才可用， 否则按 0 处理，MMR 自然退化为相关性原序——无向量信息时不乱序）。
 *
 * <p>λ 约定（{@code bone.ai.rag.mmr-lambda}，默认 0 = <b>关闭</b>）：λ∈(0,1) 生效，λ=1 纯相关性 （数学退化，等价关闭）；λ=0
 * 按约定视为关闭而非数学上的纯多样性——避免运维误配 0 得到乱序结果。 阈值须按评测集回流数据校准（批二），默认关闭避免误伤（与 {@code minScore} 同哲学：宁缺毋滥、
 * 按实测标定，不拿猜测值当默认行为）。
 */
public final class MmrSelector {

  private MmrSelector() {}

  /** 候选项：引用 + 相关性分（精排分 / 融合分）+ 多样性去重向量（可为 null，相似度按 0 处理）。 */
  public record Candidate<T>(T ref, double relevance, float[] embedding) {}

  /**
   * 按 MMR 从候选中选取至多 {@code topK} 个（保持「相关性主导、多样性去重」）。
   *
   * @param candidates 候选项（调用方保证按相关性降序——迭代第一步即相关性最高者）
   * @param topK 选取数量
   * @param lambda 多样性权重 λ（≤0 或超范围按约定收敛：关闭 / 纯相关性）
   * @return 选取的引用列表（MMR 选取序，非相关性序）
   */
  public static <T> List<T> select(List<Candidate<T>> candidates, int topK, double lambda) {
    if (candidates == null || candidates.isEmpty() || topK <= 0) {
      return List.of();
    }
    double l = Math.min(Math.max(lambda, 0.0), 1.0);
    if (l <= 0.0 || candidates.size() <= topK) {
      // 关闭（λ=0 约定）或候选不足：按相关性原序截断——与未接入 MMR 行为一致
      return candidates.stream().limit(topK).map(Candidate::ref).toList();
    }
    List<Candidate<T>> remaining = new ArrayList<>(candidates);
    List<T> selected = new ArrayList<>(Math.min(topK, remaining.size()));
    List<float[]> selectedEmb = new ArrayList<>();
    while (selected.size() < topK && !remaining.isEmpty()) {
      int best = -1;
      double bestScore = -Double.MAX_VALUE;
      for (int i = 0; i < remaining.size(); i++) {
        Candidate<T> c = remaining.get(i);
        double maxSim = selectedEmb.isEmpty() ? 0.0 : -Double.MAX_VALUE;
        for (float[] e : selectedEmb) {
          double sim = CosineSimilarity.of(c.embedding(), e);
          if (sim > maxSim) {
            maxSim = sim;
          }
        }
        double score = l * c.relevance() - (1.0 - l) * maxSim;
        if (score > bestScore) {
          bestScore = score;
          best = i;
        }
      }
      Candidate<T> picked = remaining.remove(best);
      selected.add(picked.ref());
      if (picked.embedding() != null && picked.embedding().length > 0) {
        selectedEmb.add(picked.embedding());
      }
    }
    return selected;
  }
}
