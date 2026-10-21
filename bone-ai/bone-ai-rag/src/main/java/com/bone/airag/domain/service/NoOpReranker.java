package com.bone.airag.domain.service;

import com.bone.airag.domain.gateway.RerankPort;
import com.bone.airag.domain.model.RerankCandidate;
import com.bone.airag.domain.model.RerankScored;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认重排序实现：不精排，按入池先验分（RRF 融合分）原序返回前 {@code topK}。
 *
 * <p>未接入重排序服务时的安全回退——保持既有检索行为不变（RRF 序即最终序）， 绝不因缺 rerank 而丢结果或改变引用强制语义。
 */
public class NoOpReranker implements RerankPort {

  @Override
  public List<RerankScored> rerank(String query, List<RerankCandidate> candidates, int topK) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    int k = Math.max(0, Math.min(topK, candidates.size()));
    List<RerankScored> out = new ArrayList<>(k);
    for (int i = 0; i < k; i++) {
      RerankCandidate c = candidates.get(i);
      out.add(new RerankScored(c.ref(), c.priorScore()));
    }
    return out;
  }
}
