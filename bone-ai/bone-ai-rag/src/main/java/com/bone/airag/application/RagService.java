package com.bone.airag.application;

import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.Bm25Scorer;
import com.bone.airag.domain.service.CosineSimilarity;
import com.bone.airag.domain.service.RrfFuser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 受控检索服务：租户/权限过滤 → 向量 + BM25 双路 → RRF 融合 → 引用强制 → 生成。
 *
 * <p>「引用强制」为 fail-closed：任何结果切片若缺失 {@code clauseRef} 或 span 非法，直接丢弃， 绝不以无出处内容喂给生成（详设
 * §2.6「无出处不得生成」）。
 */
public class RagService {

  private static final Logger log = LoggerFactory.getLogger(RagService.class);
  private static final int RRF_K = 60;
  private static final double W_VECTOR = 0.5;
  private static final double W_BM25 = 0.5;

  private final ChunkRepository chunkRepository;
  private final EmbeddingPort embeddingPort;
  private final TenantProvider tenantProvider;

  public RagService(
      ChunkRepository chunkRepository, EmbeddingPort embeddingPort, TenantProvider tenantProvider) {
    this.chunkRepository = chunkRepository;
    this.embeddingPort = embeddingPort;
    this.tenantProvider = tenantProvider;
  }

  public List<RagResult> retrieve(RagQuery query) {
    Long tenantId = tenantProvider.currentTenantId();
    List<RagChunk> chunks = chunkRepository.findByTenantAndDocScope(tenantId, query.docScope());
    if (chunks.isEmpty()) {
      return List.of();
    }

    float[] queryVec = embeddingPort.embed(query.query());
    Map<String, RagChunk> byKey =
        chunks.stream().collect(Collectors.toMap(c -> String.valueOf(c.getId()), c -> c));
    Map<String, Double> vectorScores = new HashMap<>();
    Map<String, Double> bm25Scores = new HashMap<>();
    List<String> corpusTexts = chunks.stream().map(RagChunk::getText).collect(Collectors.toList());
    for (RagChunk c : chunks) {
      String key = String.valueOf(c.getId());
      vectorScores.put(key, CosineSimilarity.of(queryVec, c.parseEmbedding()));
      bm25Scores.put(key, Bm25Scorer.score(query.query(), c.getText(), corpusTexts));
    }

    Map<String, Double> fused = RrfFuser.fuse(vectorScores, bm25Scores, RRF_K, W_VECTOR, W_BM25);

    List<RagResult> results = new ArrayList<>();
    for (Map.Entry<String, Double> e : fused.entrySet()) {
      if (results.size() >= query.topK()) {
        break;
      }
      RagChunk c = byKey.get(e.getKey());
      if (c == null) {
        continue;
      }
      // 引用强制：无 clauseRef / 非法 span ⇒ fail-closed 丢弃（无出处不得生成）
      if (c.getClauseRef() == null
          || c.getClauseRef().isBlank()
          || c.getSpanEnd() <= c.getSpanStart()) {
        log.warn("RAG 引用强制拦截：chunk={} 无有效出处，丢弃", e.getKey());
        continue;
      }
      results.add(
          new RagResult(
              e.getKey(),
              c.getClauseRef(),
              c.getSpanStart(),
              c.getSpanEnd(),
              e.getValue(),
              c.getSectionPath(),
              c.getText()));
    }
    return results;
  }
}
