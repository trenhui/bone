package com.bone.airag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.airag.application.RagApplicationService.RagRetrieveOutcome;
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
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.infrastructure.embedding.HttpBgeEmbeddingClient;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RagServiceTest {

  /** 桩精排器：按 ref→score 打分降序返回前 topK；{@code fail=true} 模拟 provider 故障。 */
  private static final class StubReranker implements RerankPort {
    private final Map<String, Double> scores;
    private final boolean fail;

    private StubReranker(Map<String, Double> scores, boolean fail) {
      this.scores = scores;
      this.fail = fail;
    }

    @Override
    public List<RerankScored> rerank(String query, List<RerankCandidate> candidates, int topK) {
      if (fail) {
        throw new RuntimeException("rerank provider down");
      }
      return candidates.stream()
          .map(c -> new RerankScored(c.ref(), scores.getOrDefault(c.ref(), 0.0)))
          .sorted((a, b) -> Double.compare(b.score(), a.score()))
          .limit(topK)
          .toList();
    }
  }

  /** 记录型事件下沉桩：验证降级 / 数据卫生事件确实发生（而非只躺日志）。 */
  private static final class RecordingSink implements RagEventSink {
    private final List<RagEvent> events = new ArrayList<>();

    @Override
    public void record(RagEvent event) {
      events.add(event);
    }
  }

  private static RagChunk chunk(
      Long id, String docId, String docVersion, String text, float[] vec, String clauseRef) {
    return RagChunk.of(
        id,
        42L,
        docId,
        docVersion,
        "Clause " + id,
        text,
        vec,
        "hash-64",
        clauseRef,
        0,
        10);
  }

  /** 默认装配：无文档级 ACL（仅租户硬隔离）+ 记录型事件 sink + MMR 关闭 + 容量/慢检索预警关闭。 */
  private RagApplicationService service(
      ChunkRepository repo, EmbeddingPort embed, RerankPort rerank, TenantProvider tenant) {
    return service(repo, embed, rerank, tenant, new NoDocAcl(), new RecordingSink(), 0.0, 0, -1);
  }

  private RagApplicationService service(
      ChunkRepository repo,
      EmbeddingPort embed,
      RerankPort rerank,
      TenantProvider tenant,
      RagAclPort acl,
      RagEventSink sink,
      double mmrLambda) {
    return service(repo, embed, rerank, tenant, acl, sink, mmrLambda, 0, -1);
  }

  /**
   * 全参装配。
   *
   * @param warnScanThreshold 容量预警阈值（切片数）；0/负关闭
   * @param slowRetrieveMs 慢检索阈值（毫秒）；负数关闭，0 = 任意正耗时告警，正数 = 生产水位
   */
  private RagApplicationService service(
      ChunkRepository repo,
      EmbeddingPort embed,
      RerankPort rerank,
      TenantProvider tenant,
      RagAclPort acl,
      RagEventSink sink,
      double mmrLambda,
      int warnScanThreshold,
      long slowRetrieveMs) {
    return new RagApplicationService(
        repo, embed, rerank, tenant, acl, sink, 30, mmrLambda, warnScanThreshold, slowRetrieveMs);
  }

  @Test
  void ranks_by_fused_score_and_enforces_citation() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    // a 与查询同向且文本含查询词（余弦 + BM25 双路均最优）；b 正交且无查询词；c 无 clauseRef（引用强制应剔除）
    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "gamma delta", new float[] {0f, 1f, 0f}, "clause-2");
    RagChunk c = chunk(3L, "doc1", "v1", "epsilon", new float[] {0f, 0f, 1f}, null);

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b, c));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);
    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 10));

    // c 因引用强制前置被剔除；a 因双路匹配排第一
    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "2");
    assertThat(outcome.results().get(0).clauseRef()).isEqualTo("clause-1");
    assertThat(outcome.degradations()).isEmpty();
  }

  @Test
  void rerank_reorders_and_its_score_is_final() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "query beta", new float[] {0.9f, 0.1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    // RRF 粗序 a 在前；cross-encoder 精排把 b 打到第一（0.9 > 0.1），且最终分取 rerank 分
    Map<String, Double> scores = new HashMap<>();
    scores.put("1", 0.1);
    scores.put("2", 0.9);
    RagApplicationService service = service(repo, embed, new StubReranker(scores, false), tenant);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("2", "1");
    assertThat(outcome.results().get(0).score()).isEqualTo(0.9);
  }

  @Test
  void rerank_failure_degrades_to_rrf_order() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);
    RecordingSink sink = new RecordingSink();

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "gamma delta", new float[] {0f, 1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    // rerank provider 故障：不抛给调用方，回落 RRF 粗序（a 仍第一，分取融合分）
    RagApplicationService service =
        service(repo, embed, new StubReranker(Map.of(), true), tenant, new NoDocAcl(), sink, 0.0);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "2");
    assertThat(outcome.results().get(0).score()).isGreaterThan(outcome.results().get(1).score());
    // 降级可观测：痕迹码进响应 + 事件下沉（非只躺 WARN 日志）
    assertThat(outcome.degradations()).containsExactly(RagApplicationService.DEGRADE_RERANK);
    assertThat(sink.events).extracting(RagEvent::type).containsExactly(RagApplicationService.DEGRADE_RERANK);
  }

  @Test
  void embedding_failure_degrades_to_bm25_only() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);
    RecordingSink sink = new RecordingSink();

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "gamma delta", new float[] {0f, 1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.embed("query")).thenThrow(new RuntimeException("embedding provider down"));

    // 嵌入 provider 故障：不抛给调用方，回落 BM25 单路（query 词命中 a）
    RagApplicationService service =
        service(repo, embed, new NoOpReranker(), tenant, new NoDocAcl(), sink, 0.0);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "2");
    assertThat(outcome.degradations()).containsExactly(RagApplicationService.DEGRADE_EMBEDDING);
    assertThat(sink.events)
        .extracting(RagEvent::type)
        .containsExactly(RagApplicationService.DEGRADE_EMBEDDING);
  }

  @Test
  void min_score_filters_low_relevance() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "query beta", new float[] {0.9f, 0.1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    Map<String, Double> scores = new HashMap<>();
    scores.put("1", 0.9);
    scores.put("2", 0.1);
    RagApplicationService service = service(repo, embed, new StubReranker(scores, false), tenant);

    // minScore=0.5：0.1 的 b 被过滤（宁缺毋滥，返回数可小于 topK）
    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5, 0.5, 10_000));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1");
  }

  @Test
  void min_score_skipped_when_rerank_degraded_to_avoid_empty_results() {
    // 诊断复核 v1.2：rerank 降级时最终分为 RRF 融合分（~0.01 量级），若仍按 rerank 量纲阈值过滤
    // 会把全部结果误杀成空 → 事故期质量腰斩。故降级时应跳过 minScore。
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);
    RecordingSink sink = new RecordingSink();

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "gamma delta", new float[] {0f, 1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    // rerank provider 故障 + 高 minScore（0.9，按 rerank 量纲会全杀）
    RagApplicationService service =
        service(repo, embed, new StubReranker(Map.of(), true), tenant, new NoDocAcl(), sink, 0.0);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5, 0.9, 10_000));

    // 降级时跳过 minScore：结果不为空（a, b 全保留），且可观测降级痕迹
    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "2");
    assertThat(outcome.degradations()).containsExactly(RagApplicationService.DEGRADE_RERANK);
  }

  @Test
  void min_score_skipped_when_no_rerank_configured() {
    // 默认 NoOpReranker（未配 rerank）：最终分为 RRF 融合分，minScore 同样不应误杀。
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "gamma delta", new float[] {0f, 1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5, 0.9, 10_000));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "2");
    assertThat(outcome.degradations()).isEmpty();
  }

  @Test
  void min_score_applied_only_on_rerank_scale() {
    // 反向确认：rerank 正常（cross-encoder 分）时，minScore 仍按原契约生效。
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "query beta", new float[] {0.9f, 0.1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    Map<String, Double> scores = new HashMap<>();
    scores.put("1", 0.9);
    scores.put("2", 0.1);
    RagApplicationService service = service(repo, embed, new StubReranker(scores, false), tenant);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5, 0.5, 10_000));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1");
  }

  @Test
  void max_scan_exceeded_fails_closed() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "query beta", new float[] {0.9f, 0.1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);

    // maxScan=1 < 2 个切片：fail-closed 拒绝，绝不静默截断（错误码断言走 getErrorCode，勿用消息包含）
    assertThatThrownBy(() -> service.retrieve(new RagQuery("query", null, 5, 0.0, 1)))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiRagErrorCodes.SCOPE_OVER_LIMIT));
    verify(embed, never()).embed(any());
  }

  @Test
  void doc_scope_not_visible_fails_closed_403() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    when(tenant.currentTenantId()).thenReturn(42L);
    // 调用方可见集合只有 doc-2；显式索要 doc-1 ⇒ fail-closed 403（绝不静默回落全租户检索）
    RagAclPort acl = t -> Set.of("doc-2");

    RagApplicationService service =
        service(repo, embed, new NoOpReranker(), tenant, acl, new RecordingSink(), 0.0);

    assertThatThrownBy(() -> service.retrieve(new RagQuery("query", "doc-1", 5)))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiRagErrorCodes.DOC_FORBIDDEN));
    // fail-fast：ACL 拒绝发生在任何仓储查询 / 推理调用之前
    verify(repo, never()).findByTenantAndDocScope(any(), any());
    verify(embed, never()).embed(any());
  }

  @Test
  void unscoped_retrieve_filters_invisible_docs() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk a = chunk(1L, "doc-1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc-2", "v1", "query beta", new float[] {0f, 1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});
    // 无显式作用域：按可见集合过滤——doc-2 的切片不进候选（同租户内越权面闭合）
    RagAclPort acl = t -> Set.of("doc-1");

    RagApplicationService service =
        service(repo, embed, new NoOpReranker(), tenant, acl, new RecordingSink(), 0.0);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 5));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1");
  }

  @Test
  void mmr_diversifies_top_k() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    // c1 相关性最高；c2 与 c1 高度相似（同质）；c3 与 c1 正交（异质）但相关性略低
    RagChunk c1 = chunk(1L, "doc1", "v1", "alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk c2 = chunk(2L, "doc1", "v1", "beta", new float[] {0.99f, 0.1f, 0f}, "clause-2");
    RagChunk c3 = chunk(3L, "doc1", "v1", "gamma", new float[] {0f, 1f, 0f}, "clause-3");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(c1, c2, c3));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    Map<String, Double> scores = new HashMap<>();
    scores.put("1", 0.9);
    scores.put("2", 0.8);
    scores.put("3", 0.7);

    // λ=0.5：c1 先入选；c2 因与 c1 高度相似被惩罚，c3（正交）胜出——防 top-K 同质化
    RagApplicationService service =
        service(repo, embed, new StubReranker(scores, false), tenant, new NoDocAcl(), new RecordingSink(), 0.5);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 2));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "3");
    // MMR 只改选取序，不改分数语义：score 仍是精排分
    assertThat(outcome.results().get(1).score()).isEqualTo(0.7);
  }

  @Test
  void mmr_disabled_keeps_relevance_order() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    RagChunk c1 = chunk(1L, "doc1", "v1", "alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk c2 = chunk(2L, "doc1", "v1", "beta", new float[] {0.99f, 0.1f, 0f}, "clause-2");
    RagChunk c3 = chunk(3L, "doc1", "v1", "gamma", new float[] {0f, 1f, 0f}, "clause-3");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(c1, c2, c3));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    Map<String, Double> scores = new HashMap<>();
    scores.put("1", 0.9);
    scores.put("2", 0.8);
    scores.put("3", 0.7);

    // λ=0（默认关闭）：与未接入 MMR 行为一致——纯相关性序 [1, 2]
    RagApplicationService service = service(repo, embed, new StubReranker(scores, false), tenant);

    RagRetrieveOutcome outcome = service.retrieve(new RagQuery("query", null, 2));

    assertThat(outcome.results()).extracting(RagResult::chunkRef).containsExactly("1", "2");
  }

  @Test
  void ingest_same_version_same_count_is_idempotent_skip() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    when(repo.findByTenantAndDoc(42L, "doc1"))
        .thenReturn(
            List.of(
                chunk(10L, "doc1", "v1", "alpha", new float[] {1f}, "clause-1"),
                chunk(11L, "doc1", "v1", "beta", new float[] {1f}, "clause-2")));

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);

    service.ingest(
        42L,
        "doc1",
        "v1",
        List.of(
            new RagApplicationService.RagIngestSegment("S1", "alpha", "clause-1", 0, 10),
            new RagApplicationService.RagIngestSegment("S2", "beta", "clause-2", 0, 10)));

    verify(repo, never()).deleteByIds(anyList());
    verify(repo, never()).save(any());
  }

  @Test
  void ingest_new_version_soft_deletes_stale_then_rebuilds() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    when(repo.findByTenantAndDoc(42L, "doc1"))
        .thenReturn(List.of(chunk(10L, "doc1", "v1", "alpha", new float[] {1f}, "clause-1")));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embedBatch(anyList())).thenReturn(List.of(new float[] {1f}));

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);

    service.ingest(
        42L,
        "doc1",
        "v2",
        List.of(new RagApplicationService.RagIngestSegment("S1", "alpha-v2", "clause-1", 0, 10)));

    verify(repo).deleteByIds(List.of(10L));
    ArgumentCaptor<RagChunk> captor = ArgumentCaptor.forClass(RagChunk.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getDocVersion()).isEqualTo("v2");
    assertThat(captor.getValue().getText()).isEqualTo("alpha-v2");
  }

  @Test
  void ingest_partial_same_version_rebuilds() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    // 上次崩溃半截：v1 只落了 1 条，本次应判「残缺」重建（自愈）
    when(repo.findByTenantAndDoc(42L, "doc1"))
        .thenReturn(List.of(chunk(10L, "doc1", "v1", "alpha", new float[] {1f}, "clause-1")));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embedBatch(anyList())).thenReturn(List.of(new float[] {1f}, new float[] {1f}));

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);

    service.ingest(
        42L,
        "doc1",
        "v1",
        List.of(
            new RagApplicationService.RagIngestSegment("S1", "alpha", "clause-1", 0, 10),
            new RagApplicationService.RagIngestSegment("S2", "beta", "clause-2", 0, 10)));

    verify(repo).deleteByIds(List.of(10L));
    verify(repo, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  void scope_near_limit_emits_event_when_above_threshold() {
    // 规模止血可观测（诊断 A）：作用域切片数超过 warnScanThreshold（但未触 maxScan）即发 SCOPE_NEAR_LIMIT，
    // 撞上限前先给信号（引导下调 max-scan / 推进 R4 索引化），而非等 OOM/超时爆发。
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);
    RecordingSink sink = new RecordingSink();

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");
    RagChunk b = chunk(2L, "doc1", "v1", "query beta", new float[] {0.9f, 0.1f, 0f}, "clause-2");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    // warnScanThreshold=1 < 2 切片：预警但不 fail-closed（maxScan 默认 10000）
    RagApplicationService service =
        service(repo, embed, new NoOpReranker(), tenant, new NoDocAcl(), sink, 0.0, 1, -1);

    service.retrieve(new RagQuery("query", null, 5));

    assertThat(sink.events)
        .extracting(RagEvent::type)
        .containsExactly(RagApplicationService.EVENT_SCOPE_NEAR_LIMIT);
  }

  @Test
  void slow_retrieve_emits_event() throws Exception {
    // 性能预警（诊断 A）：墙钟超 slowRetrieveMs 即发 RETRIEVE_SLOW，供落 trace / 接 P99 告警。
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);
    RecordingSink sink = new RecordingSink();

    RagChunk a = chunk(1L, "doc1", "v1", "query alpha", new float[] {1f, 0f, 0f}, "clause-1");

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a));
    when(embed.version()).thenReturn("hash-64");
    // 人为放慢嵌入，确保墙钟 > slowRetrieveMs
    when(embed.embed(any()))
        .thenAnswer(
            inv -> {
              Thread.sleep(5);
              return new float[] {1f, 0f, 0f};
            });

    // slowRetrieveMs=1：5ms 嵌入使墙钟超阈值 → 发 RETRIEVE_SLOW
    RagApplicationService service =
        service(repo, embed, new NoOpReranker(), tenant, new NoDocAcl(), sink, 0.0, 0, 1);

    service.retrieve(new RagQuery("query", null, 5));

    assertThat(sink.events)
        .extracting(RagEvent::type)
        .containsExactly(RagApplicationService.EVENT_RETRIEVE_SLOW);
  }

  @Test
  void ingest_batch_embedding_count_mismatch_fails_closed() {
    // 批量嵌入 fail-closed（诊断 B19）：批返回数量与切片数不符时上抛，旧版本不动（不留下半截新版本）。
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    when(repo.findByTenantAndDoc(42L, "doc1")).thenReturn(List.of());
    when(embed.version()).thenReturn("hash-64");
    // 输入 2 切片，仅返回 1 向量：应 fail-closed
    when(embed.embedBatch(anyList())).thenReturn(List.of(new float[] {1f}));

    RagApplicationService service = service(repo, embed, new NoOpReranker(), tenant);

    assertThatThrownBy(
            () ->
                service.ingest(
                    42L,
                    "doc1",
                    "v1",
                    List.of(
                        new RagApplicationService.RagIngestSegment("S1", "alpha", "clause-1", 0, 10),
                        new RagApplicationService.RagIngestSegment("S2", "beta", "clause-2", 0, 10))))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL));
    // 嵌入未成功前绝不动旧版本
    verify(repo, never()).deleteByIds(anyList());
    verify(repo, never()).save(any());
  }

  @Test
  void bge_batch_parse_roundtrip() {
    // 批量响应解析（HttpBgeEmbeddingClient.embedBatch 依赖）：[[..],[..]] → 向量列表。
    List<float[]> parsed = HttpBgeEmbeddingClient.parseEmbeddings("[[0.1,0.2],[0.3,0.4]]");
    assertThat(parsed).hasSize(2);
    assertThat(parsed.get(0)).containsExactly(0.1f, 0.2f);
    assertThat(parsed.get(1)).containsExactly(0.3f, 0.4f);
  }

  @Test
  void bge_batch_parse_rejects_malformed() {
    // 结构非法（非外层数组）应抛 EMBEDDING_PROVIDER_FAIL，而非静默返回空。
    assertThatThrownBy(() -> HttpBgeEmbeddingClient.parseEmbeddings("{\"x\":1}"))
        .isInstanceOf(com.bone.core.exception.BizException.class);
  }
}
