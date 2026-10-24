package com.bone.airag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.GroundingGuard;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 检索双路并发回归（R7，2026-10-24）：向量路（嵌入调用，触网）与 BM25 路（CPU）须并发执行。
 *
 * <p>改动前为顺序执行：先阻塞等 provider 返回查询向量，再遍历语料做 BM25——两路无数据依赖，墙钟 = 嵌入耗时 + BM25
 * 耗时。本测试用两条互补证据锁死并发语义，任一条被破坏（改回顺序 / 提交后立即 join 的假并行）都会红：
 *
 * <ol>
 *   <li><b>线程证据（确定性）</b>：{@code embed} 必须运行在调用线程之外。顺序实现里 {@code embeddingPort.embed} 在 {@code
 *       retrieve} 调用栈内联执行，跑在调用线程上；卸载到 {@code CompletableFuture} 后必在池线程。
 *   <li><b>时序证据（基线校准）</b>：先以零延迟嵌入实测一次 BM25 基线耗时，再以注入延迟的嵌入跑完整链路—— 并发时总耗时 ≈ 嵌入延迟（BM25 被重叠吸收），顺序时 ≈
 *       嵌入延迟 + BM25 基线。阈值取 {@code 延迟 + 0.6×基线}：并发有充分余量，顺序必超。
 * </ol>
 */
class RetrieveConcurrencyTest {

  private static final String CLAUSE_BASE = "甲方逾期付款的，每逾期一日按未付金额千分之一支付违约金";
  private static final int CORPUS_SIZE = 400;
  private static final long EMBED_SLEEP_MS = 400L;

  /** 记录嵌入调用线程名 + 可注入网络延迟的桩（版本与切片一致 ⇒ 向量路真实参与）。 */
  private static final class ThreadRecordingEmbedding implements EmbeddingPort {
    private final long sleepMs;
    private volatile String embedThread;

    private ThreadRecordingEmbedding(long sleepMs) {
      this.sleepMs = sleepMs;
    }

    @Override
    public String version() {
      return "hash-64";
    }

    @Override
    public float[] embed(String text) {
      embedThread = Thread.currentThread().getName();
      if (sleepMs > 0) {
        try {
          Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return new float[] {1f, 0f};
    }
  }

  @Test
  void vector_path_runs_concurrently_with_bm25_scoring() {
    String callerThread = Thread.currentThread().getName();
    ChunkRepository repo = mock(ChunkRepository.class);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(corpus());
    TenantProvider tenant = mock(TenantProvider.class);
    when(tenant.currentTenantId()).thenReturn(42L);

    // 基线：零延迟嵌入实测一次 BM25 段耗时（吸收 JIT 预热，作为时序断言的标尺）
    long baselineMs = elapsedMs(new ThreadRecordingEmbedding(0), repo, tenant);

    ThreadRecordingEmbedding sleeping = new ThreadRecordingEmbedding(EMBED_SLEEP_MS);
    long elapsedMs = elapsedMs(sleeping, repo, tenant);

    // 证据 1（确定性）：嵌入必须跑在调用线程之外
    assertThat(sleeping.embedThread)
        .as("embed 应卸载到公共池线程执行；若跑在调用线程上说明退化为顺序执行")
        .isNotNull()
        .isNotEqualTo(callerThread);

    // 证据 2（时序）：总耗时应约等于嵌入延迟（BM25 被并发吸收），而非 延迟 + 基线
    long threshold = (long) (EMBED_SLEEP_MS + baselineMs * 0.6);
    assertThat(elapsedMs)
        .as(
            "检索总耗时 %dms 接近顺序执行（嵌入 %dms + BM25 基线 %dms），双路未并发",
            elapsedMs, EMBED_SLEEP_MS, baselineMs)
        .isLessThan(threshold);
  }

  private static long elapsedMs(
      EmbeddingPort embedding, ChunkRepository repo, TenantProvider tenant) {
    RagApplicationService service =
        new RagApplicationService(
            repo,
            embedding,
            new NoOpReranker(),
            tenant,
            new NoDocAcl(),
            event -> {},
            30,
            0.0,
            0,
            -1,
            new GroundingGuard(0.6, 0.0));
    long start = System.nanoTime();
    service.retrieve(new RagQuery("逾期付款违约金", null, 5));
    return (System.nanoTime() - start) / 1_000_000;
  }

  /** 语料：同版本向量 + 带出处切片（过引用前置），文本含查询词（BM25 有话可说）。 */
  private static List<RagChunk> corpus() {
    List<RagChunk> chunks = new ArrayList<>(CORPUS_SIZE);
    for (int i = 0; i < CORPUS_SIZE; i++) {
      chunks.add(
          RagChunk.of(
              (long) i,
              42L,
              "doc-1",
              "v1",
              "Clause " + i,
              CLAUSE_BASE + "，第" + i + "号补充约定违约责任与交付标准",
              new float[] {1f, 0f},
              "hash-64",
              "clause-" + i,
              0,
              10));
    }
    return chunks;
  }
}
