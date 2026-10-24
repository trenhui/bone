package com.bone.airag.application;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.RagAclPort;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.gateway.RerankPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagEvent;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.airag.domain.model.RerankCandidate;
import com.bone.airag.domain.model.RerankScored;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.Bm25Scorer;
import com.bone.airag.domain.service.CosineSimilarity;
import com.bone.airag.domain.service.MmrSelector;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.domain.service.RrfFuser;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 受控检索服务（详设 §2.6，2026-10-23 按代码真序重写；P1 项 ACL / MMR / 降级可观测同日落地）。
 *
 * <p>检索链（一次查询）：租户过滤 → <b>文档级 ACL</b>（默认仅租户硬隔离；接入方受限可见集合后 fail-closed）→
 * <b>容量护栏</b>（{@code maxScan} 超限 fail-closed）→ <b>引用强制前置</b>（无出处切片剔除，不占精排名额）→
 * 向量 + BM25 双路（<b>嵌入故障降级 BM25 单路</b>）→ RRF 融合（k=60，0.5/0.5）→ 候选池（{@code
 * candidatePool}，默认 30）→ <b>rerank</b>（cross-encoder 精排；<b>故障降级 RRF 粗序</b>）→ {@code minScore}
 * 过滤 → <b>MMR 多样性选取</b>（λ≤0 关闭）→ topK。
 *
 * <p>降级口径（读路径可用性优先，且降级可观测——{@link RagEventSink} 事件 + 响应 {@code degradations} 痕迹，
 * 非「假装已精排」）：嵌入 / 重排序 provider 故障时分别回落<b>本域内已实现路径</b>（BM25 单路 / RRF 粗序），
 * 引用强制语义不变；<b>严禁切云端辅路</b>（数据不出域红线）。
 *
 * <p>「引用强制」为 fail-closed：任何结果切片若缺失 {@code clauseRef} 或 span 非法，直接丢弃， 绝不以无出处内容喂给生成（详设
 * §2.6「无出处不得生成」）。
 */
public class RagApplicationService {

  private static final Logger log = LoggerFactory.getLogger(RagApplicationService.class);
  private static final int RRF_K = 60;
  private static final double W_VECTOR = 0.5;
  private static final double W_BM25 = 0.5;
  private static final int DEFAULT_CANDIDATE_POOL = 30;

  /** MMR 扩样倍数：开启多样性选取时，送精排的候选数 = topK × 本系数（受候选池上限约束）。 */
  private static final int MMR_EXPANSION = 3;

  /**
   * 降级痕迹码（响应 {@code degradations} + {@link RagEvent#type()}）：嵌入 provider 故障，向量路不可用，
   * 本次检索回落 BM25 单路（引用强制语义不变）。
   */
  public static final String DEGRADE_EMBEDDING = "EMBEDDING_DEGRADED";

  /** 降级痕迹码：重排序 provider 故障，本次检索回落 RRF 粗序（引用强制语义不变）。 */
  public static final String DEGRADE_RERANK = "RERANK_DEGRADED";

  /** 事件码（非降级，数据卫生）：引用强制前置剔除无出处切片。 */
  public static final String EVENT_DROP_INVALID_CHUNKS = "DROP_INVALID_CHUNKS";

  /**
   * 事件码（容量预警，非 fail-closed）：作用域切片数已超过 {@code warnScanThreshold} 但仍未触 maxScan。
   *
   * <p>过渡态每次检索全量载入内存打分——切片数在几千量级时延迟已悄然劣化却无信号。该事件在撞上限前先给可观测
   * 预警，引导运维下调 {@code max-scan} 或推进 R4（pgvector + ANN 索引化），而非等 OOM/超时爆发。
   */
  public static final String EVENT_SCOPE_NEAR_LIMIT = "SCOPE_NEAR_LIMIT";

  /**
   * 事件码（性能预警）：单次 retrieve 墙钟耗时超过 {@code slowRetrieveMs}。
   *
   * <p> diagnosing 规模瓶颈的抓手——全量内存 BM25 + 余弦下，切片数增长会线性推高延迟；该事件直接暴露慢查询，
   * 供接入方落 trace / 接 P99 告警（与 {@code SCOPE_NEAR_LIMIT} 联动定位「是容量还是别的」）。
   */
  public static final String EVENT_RETRIEVE_SLOW = "RETRIEVE_SLOW";

  private final ChunkRepository chunkRepository;
  private final EmbeddingPort embeddingPort;
  private final RerankPort rerankPort;
  private final TenantProvider tenantProvider;
  private final RagAclPort aclPort;
  private final RagEventSink eventSink;
  private final int candidatePool;
  private final double mmrLambda;

  /** 容量预警阈值：作用域切片数超过此值（且未触 maxScan）即发 {@link #EVENT_SCOPE_NEAR_LIMIT}。<=0 关闭。 */
  private final int warnScanThreshold;

  /** 慢检索阈值（毫秒）：单次 retrieve 墙钟超过此值即发 {@link #EVENT_RETRIEVE_SLOW}。负数关闭（默认测试 -1）；0 = 任意正耗时都告警（极敏感/调试）；正数 = 生产水位（如 500）。 */
  private final long slowRetrieveMs;

  public RagApplicationService(
      ChunkRepository chunkRepository,
      EmbeddingPort embeddingPort,
      RerankPort rerankPort,
      TenantProvider tenantProvider,
      RagAclPort aclPort,
      RagEventSink eventSink,
      int candidatePool,
      double mmrLambda,
      int warnScanThreshold,
      long slowRetrieveMs) {
    this.chunkRepository = chunkRepository;
    this.embeddingPort = embeddingPort;
    this.rerankPort = rerankPort;
    this.tenantProvider = tenantProvider;
    this.aclPort = aclPort;
    this.eventSink = eventSink;
    this.candidatePool = candidatePool <= 0 ? DEFAULT_CANDIDATE_POOL : candidatePool;
    this.mmrLambda = mmrLambda < 0 ? 0.0 : mmrLambda;
    this.warnScanThreshold = warnScanThreshold;
    this.slowRetrieveMs = slowRetrieveMs;
  }

  /**
   * 检索结果 + 降级痕迹（P1-10：调用方 / 评测管线可观测本次检索是否发生能力档位下降）。
   *
   * @param results 命中切片（引用强制 + minScore + MMR 之后）
   * @param degradations 降级痕迹码（{@link #DEGRADE_EMBEDDING} / {@link #DEGRADE_RERANK}；空 = 全功能档位）
   */
  public record RagRetrieveOutcome(List<RagResult> results, List<String> degradations) {}

  public RagRetrieveOutcome retrieve(RagQuery query) {
    long startNanos = System.nanoTime();
    Long tenantId = tenantProvider.currentTenantId();

    // 文档级 ACL（fail-closed）：默认仅租户硬隔离；接入方提供受限可见集合后，无 ACL 归属的 doc 不可见
    Set<String> visibleDocs = aclPort.visibleDocIds(tenantId);
    List<RagChunk> chunks;
    if (query.docScope() != null && !query.docScope().isBlank()) {
      if (visibleDocs != null && !visibleDocs.contains(query.docScope())) {
        // 绝不静默回落全租户检索——那会把越权请求变成合法请求
        throw new BizException(
            403,
            "文档不在当前调用方可见范围（doc=" + query.docScope() + "）",
            AiRagErrorCodes.DOC_FORBIDDEN);
      }
      chunks = chunkRepository.findByTenantAndDocScope(tenantId, query.docScope());
    } else {
      chunks = chunkRepository.findByTenantAndDocScope(tenantId, null);
      if (visibleDocs != null) {
        int before = chunks.size();
        chunks =
            chunks.stream()
                .filter(c -> visibleDocs.contains(c.getDocId()))
                .collect(Collectors.toList());
        if (chunks.size() < before) {
          log.debug(
              "RAG ACL 过滤不可见文档切片：tenant={}, dropped={}, kept={}",
              tenantId,
              before - chunks.size(),
              chunks.size());
        }
      }
    }

    // 容量护栏（fail-closed）：过渡态全量载入内存打分，超限即拒——绝不静默截断
    if (chunks.size() > query.maxScan()) {
      throw new BizException(
          422,
          "检索作用域切片数超限（"
              + chunks.size()
              + " > "
              + query.maxScan()
              + "），请收窄 doc_scope 或调整 bone.ai.rag.max-scan",
          AiRagErrorCodes.SCOPE_OVER_LIMIT);
    }

    // 容量预警（非 fail-closed）：撞 maxScan 前先给可观测信号，引导下调阈值或推进 R4 索引化
    if (warnScanThreshold > 0 && chunks.size() > warnScanThreshold) {
      eventSink.record(
          new RagEvent(
              EVENT_SCOPE_NEAR_LIMIT,
              "tenant="
                  + tenantId
                  + ", chunks="
                  + chunks.size()
                  + " > warnScanThreshold="
                  + warnScanThreshold
                  + "（建议下调 bone.ai.rag.max-scan 或推进 R4 向量索引化）"));
    }

    // 引用强制前置：无出处切片在打分前剔除（不占 rerank 名额、不污染 BM25 语料统计）
    int dropped = 0;
    List<RagChunk> valid = new ArrayList<>(chunks.size());
    for (RagChunk c : chunks) {
      if (c.getClauseRef() == null
          || c.getClauseRef().isBlank()
          || c.getSpanEnd() <= c.getSpanStart()) {
        dropped++;
        continue;
      }
      valid.add(c);
    }
    if (dropped > 0) {
      eventSink.record(
          new RagEvent(
              EVENT_DROP_INVALID_CHUNKS,
              "tenant=" + tenantId + ", dropped=" + dropped + ", kept=" + valid.size()));
    }
    if (valid.isEmpty()) {
      return new RagRetrieveOutcome(List.of(), List.of());
    }

    List<String> degradations = new ArrayList<>(2);

    // 向量路：provider 故障 ⇒ WARN 事件 + 降级 BM25 单路（读路径可用性优先；禁切云端红线不变）
    // 版本号单独取（version() 不触网）：即使 embed 失败，只要版本已知，切片自带的同版本向量仍可支撑 MMR 多样性
    float[] queryVec = null;
    String currentVer;
    try {
      currentVer = embeddingPort.version();
    } catch (RuntimeException e) {
      currentVer = null;
    }
    try {
      queryVec = embeddingPort.embed(query.query());
    } catch (RuntimeException e) {
      eventSink.record(
          new RagEvent(
              DEGRADE_EMBEDDING,
              "tenant=" + tenantId + ", reason=" + e.getMessage() + "（回落 BM25 单路）"));
      degradations.add(DEGRADE_EMBEDDING);
      queryVec = null;
    }

    Map<String, RagChunk> byKey =
        valid.stream().collect(Collectors.toMap(c -> String.valueOf(c.getId()), c -> c));
    Map<String, Double> vectorScores = new HashMap<>();
    Map<String, Double> bm25Scores = new HashMap<>();
    // 语料统计每查询只算一次（BM25 O(N²) → O(N)，2026-10-17 复核修复）
    Bm25Scorer.CorpusStats stats =
        Bm25Scorer.CorpusStats.of(valid.stream().map(RagChunk::getText).collect(Collectors.toList()));
    for (RagChunk c : valid) {
      String key = String.valueOf(c.getId());
      // 嵌入版本不符（换模型后旧向量维度不一致）或向量路已降级：该切片不入向量路——
      // RrfFuser 对「非空路里缺失的键」按末位之后计（最弱），BM25 序主导，避免静默 0 分
      if (queryVec != null && c.getEmbeddingVer() != null && c.getEmbeddingVer().equals(currentVer)) {
        vectorScores.put(key, CosineSimilarity.of(queryVec, c.parseEmbedding()));
      } else if (queryVec != null) {
        log.warn(
            "RAG 跳过切片向量路：chunk={} embedding_ver={} 与当前 {} 不符",
            key,
            c.getEmbeddingVer(),
            currentVer);
      }
      bm25Scores.put(key, Bm25Scorer.score(query.query(), c.getText(), stats));
    }

    Map<String, Double> fused = RrfFuser.fuse(vectorScores, bm25Scores, RRF_K, W_VECTOR, W_BM25);

    // 候选池：RRF 粗序取前 candidatePool（默认 30）送 cross-encoder 精排（top-30 → top-5）
    // 池下限 topK（保证精排有足够候选可返 topK）、上限 fused 总数（防越界）
    List<Map.Entry<String, Double>> fusedOrdered = new ArrayList<>(fused.entrySet());
    int pool = Math.min(Math.max(query.topK(), candidatePool), fusedOrdered.size());
    List<RerankCandidate> candidates = new ArrayList<>(pool);
    for (int i = 0; i < pool; i++) {
      Map.Entry<String, Double> e = fusedOrdered.get(i);
      candidates.add(new RerankCandidate(e.getKey(), byKey.get(e.getKey()).getText(), e.getValue()));
    }

    // 精排：provider 故障 ⇒ WARN 事件 + 回落 NoOp（RRF 粗序），引用强制语义不变
    // MMR 开启时按倍数扩样送精排——选取式 MMR 需要比 topK 更大的候选面才能「换掉」同质项
    // （关闭时不多要，避免白付 cross-encoder 推理成本）
    int rerankTopK = query.topK();
    if (mmrLambda > 0) {
      rerankTopK =
          Math.min(candidatePool, Math.max(query.topK(), query.topK() * MMR_EXPANSION));
    }
    List<RerankScored> ranked;
    try {
      ranked = rerankPort.rerank(query.query(), candidates, rerankTopK);
    } catch (RuntimeException e) {
      eventSink.record(
          new RagEvent(
              DEGRADE_RERANK,
              "tenant=" + tenantId + ", reason=" + e.getMessage() + "（回落 RRF 粗序）"));
      degradations.add(DEGRADE_RERANK);
      ranked = new NoOpReranker().rerank(query.query(), candidates, query.topK());
    }

    // 分数阈值（宁缺毋滥）：低于阈值不进候选——交由生成侧走「无依据」路径
    // 量纲安全（诊断复核 v1.2 修复）：最终分有双轨——rerank 正常时为 cross-encoder 分（0~1/对数），
    // 降级或默认 NoOp 时为 RRF 融合分（~0.01）。minScore 是按 rerank 量纲标的，
    // 仅当「分数为 rerank 量纲」时才启用阈值；否则跳过阈值保留 RRF 序，
    // 避免 rerank 抖动降级时阈值误杀全部结果 → 空结果 → 事故期质量腰斩。
    boolean rerankScale = !degradations.contains(DEGRADE_RERANK) && !rerankPort.isNoOp();
    List<MmrSelector.Candidate<String>> mmrCandidates = new ArrayList<>();
    Map<String, Double> finalScore = new HashMap<>();
    for (RerankScored s : ranked) {
      RagChunk c = byKey.get(s.ref());
      if (c == null) {
        continue;
      }
      if (rerankScale && s.score() < query.minScore()) {
        log.debug(
            "RAG minScore 过滤（rerank 量纲）：chunk={}, score={} < {}",
            s.ref(),
            s.score(),
            query.minScore());
        continue;
      }
      if (!rerankScale && query.minScore() > 0) {
        log.debug(
            "RAG minScore 未生效（非 rerank 量纲，跳过阈值以免误杀）：chunk={}, score={}",
            s.ref(),
            s.score());
      }
      // MMR 多样性向量：仅取与当前嵌入模型同版本的切片向量（版本不符 / 向量路降级时按无向量处理，
      // MMR 自然退化为相关性原序——不拿可疑向量做多样性判断）
      float[] emb =
          (currentVer != null
                  && c.getEmbeddingVer() != null
                  && c.getEmbeddingVer().equals(currentVer))
              ? c.parseEmbedding()
              : null;
      mmrCandidates.add(new MmrSelector.Candidate<>(s.ref(), s.score(), emb));
      finalScore.put(s.ref(), s.score());
    }

    // MMR 多样性选取（λ≤0 关闭：按相关性原序——与未接入行为一致）
    List<String> selected = MmrSelector.select(mmrCandidates, query.topK(), mmrLambda);

    List<RagResult> results = new ArrayList<>(selected.size());
    for (String ref : selected) {
      RagChunk c = byKey.get(ref);
      if (c == null) {
        continue; // 防御：selected 只可能来自 byKey 键
      }
      results.add(
          new RagResult(
              ref,
              c.getClauseRef(),
              c.getSpanStart(),
              c.getSpanEnd(),
              finalScore.get(ref),
              c.getSectionPath(),
              c.getText()));
    }
    // 性能预警（非 fail-closed）：单次检索墙钟超阈值即发事件，供落 trace / 接 P99 告警
    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
    if (slowRetrieveMs >= 0 && elapsedMs > slowRetrieveMs) {
      eventSink.record(
          new RagEvent(
              EVENT_RETRIEVE_SLOW,
              "tenant="
                  + tenantId
                  + ", elapsedMs="
                  + elapsedMs
                  + " > slowRetrieveMs="
                  + slowRetrieveMs
                  + ", chunks="
                  + chunks.size()
                  + "（全量内存打分瓶颈，建议推进 R4 向量索引化）"));
    }
    return new RagRetrieveOutcome(results, List.copyOf(degradations));
  }

  /**
   * 入库一个文档切片集合（RAG 写入路径：版本幂等判定 → 批量嵌入 → 失效旧切片 → 持久化）。
   *
   * <p>幂等语义（{@code doc_version} 批键）：同版本且切片数一致 ⇒ 跳过（文档版本事件语义：同版本内容不变）； 异版本或残缺（如上次崩溃半截）⇒
   * 软删该文档全部旧切片后重建。写入路径 fail-closed：<b>先批量嵌入再删旧存新</b>——嵌入失败直接上抛，旧版本切片保持完好（重入时
   * 切片数不符触发重建自愈），不会留下半截新版本。
   *
   * <p>嵌入吞吐：切片向量经 {@link EmbeddingPort#embedBatch(List)} 一次性批量推理（TEI / infinity 数组输入），
   * 相比逐条 {@code embed} 显著减少推理往返、提升 GPU 利用率（大语料入库关键路径）。
   */
  public void ingest(Long tenantId, String docId, String docVersion, List<RagIngestSegment> segments) {
    List<RagChunk> existing = chunkRepository.findByTenantAndDoc(tenantId, docId);
    boolean idempotentHit =
        !existing.isEmpty()
            && existing.size() == segments.size()
            && existing.stream().allMatch(c -> docVersion.equals(c.getDocVersion()));
    if (idempotentHit) {
      log.info(
          "RAG ingest 幂等跳过：doc={}, tenant={}, version={}, chunks={}",
          docId,
          tenantId,
          docVersion,
          existing.size());
      return;
    }
    // 先批量嵌入（fail-closed：失败上抛，旧版本不动）
    String ver = embeddingPort.version();
    List<float[]> vectors = embeddingPort.embedBatch(segments.stream().map(RagIngestSegment::text).toList());
    if (vectors.size() != segments.size()) {
      throw new BizException(
          500,
          "嵌入批返回数量与切片数不符（" + vectors.size() + " != " + segments.size() + "）",
          AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
    }
    // 再失效旧切片（幂等批键不符时）
    if (!existing.isEmpty()) {
      List<Long> staleIds = existing.stream().map(RagChunk::getId).collect(Collectors.toList());
      chunkRepository.deleteByIds(staleIds);
      log.info("RAG ingest 失效旧切片：doc={}, tenant={}, stale={}", docId, tenantId, staleIds.size());
    }
    int n = 0;
    for (int i = 0; i < segments.size(); i++) {
      RagIngestSegment seg = segments.get(i);
      RagChunk chunk =
          RagChunk.of(
              null,
              tenantId,
              docId,
              docVersion,
              seg.sectionPath(),
              seg.text(),
              vectors.get(i),
              ver,
              seg.clauseRef(),
              seg.spanStart(),
              seg.spanEnd());
      chunkRepository.save(chunk);
      n++;
    }
    log.info("RAG ingested: doc={}, tenant={}, version={}, chunks={}", docId, tenantId, docVersion, n);
  }

  /** 入库切片输入（单切片：章节路径 + 文本 + 引用强制锚点）。 */
  public record RagIngestSegment(
      String sectionPath, String text, String clauseRef, int spanStart, int spanEnd) {}
}
