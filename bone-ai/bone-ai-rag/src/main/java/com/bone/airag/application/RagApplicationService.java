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
public class RagApplicationService {

  private static final Logger log = LoggerFactory.getLogger(RagApplicationService.class);
  private static final int RRF_K = 60;
  private static final double W_VECTOR = 0.5;
  private static final double W_BM25 = 0.5;

  private final ChunkRepository chunkRepository;
  private final EmbeddingPort embeddingPort;
  private final TenantProvider tenantProvider;

  public RagApplicationService(
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
    String currentVer = embeddingPort.version();
    Map<String, RagChunk> byKey =
        chunks.stream().collect(Collectors.toMap(c -> String.valueOf(c.getId()), c -> c));
    Map<String, Double> vectorScores = new HashMap<>();
    Map<String, Double> bm25Scores = new HashMap<>();
    // 语料统计每查询只算一次（BM25 O(N²) → O(N)，2026-10-17 复核修复）
    Bm25Scorer.CorpusStats stats =
        Bm25Scorer.CorpusStats.of(
            chunks.stream().map(RagChunk::getText).collect(Collectors.toList()));
    for (RagChunk c : chunks) {
      String key = String.valueOf(c.getId());
      // 嵌入版本不符（换模型后旧向量维度不一致）：fail-closed 跳过该切片向量路，仅走 BM25，避免静默 0 分
      if (c.getEmbeddingVer() == null || !c.getEmbeddingVer().equals(currentVer)) {
        log.warn(
            "RAG 跳过切片向量路：chunk={} embedding_ver={} 与当前 {} 不符",
            key,
            c.getEmbeddingVer(),
            currentVer);
        vectorScores.put(key, 0.0);
      } else {
        vectorScores.put(key, CosineSimilarity.of(queryVec, c.parseEmbedding()));
      }
      bm25Scores.put(key, Bm25Scorer.score(query.query(), c.getText(), stats));
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

  /** 入库一个文档切片集合（RAG 写入路径：边检切片 → 嵌入 → 持久化，此前缺写入面）。 */
  public void ingest(Long tenantId, String docId, List<RagIngestSegment> segments) {
    String ver = embeddingPort.version();
    int n = 0;
    for (RagIngestSegment seg : segments) {
      float[] vec = embeddingPort.embed(seg.text());
      RagChunk chunk =
          RagChunk.of(
              null,
              tenantId,
              docId,
              seg.sectionPath(),
              seg.text(),
              vec,
              ver,
              seg.clauseRef(),
              seg.spanStart(),
              seg.spanEnd());
      chunkRepository.save(chunk);
      n++;
    }
    log.info("RAG ingested: doc={}, tenant={}, chunks={}", docId, tenantId, n);
  }

  /** 入库切片输入（单切片：章节路径 + 文本 + 引用强制锚点）。 */
  public record RagIngestSegment(
      String sectionPath, String text, String clauseRef, int spanStart, int spanEnd) {}
}
