package com.bone.airag.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.GroundingGuard;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.domain.service.SensitiveDataMasker;
import com.bone.airag.infrastructure.embedding.TransitionalHashEmbedding;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * RAG 检索 golden-set 回归门禁（R5「评测前置」，随 {@code mvn clean verify} 在 CI 强制执行）。
 *
 * <p>与 Python sidecar 分工：sidecar（eval/eval_rag.py）面向真实语料 + 真模型的离线语义评测；本测试是确定性、零依赖、
 * 构建内可跑的回归门禁——用小型中文 fixture 锁死「引用强制前置 → 双路（向量+BM25）→ RRF → topK」的检索质量， 任何使预期切片掉出 topK 的回归（引用过剔 /
 * RRF 权重错乱 / 分词退化 / 阈值误杀）都会红灯。
 */
class RagGoldenSetEvalTest {

  private static final long TENANT = 1L;
  private static final String DOC_VERSION = "v1";
  private static final String EMBEDDING_VER = "hash-64-cjk";
  private static final int TOP_K = 3;

  private record GoldenCase(String query, Set<String> expected) {}

  @Test
  void golden_set_context_recall_gate() {
    TransitionalHashEmbedding embed = new TransitionalHashEmbedding();
    List<RagChunk> corpus = corpus(embed);

    ChunkRepository repo = mock(ChunkRepository.class);
    when(repo.findByTenantAndDocScope(eq(TENANT), any())).thenReturn(corpus);

    RagApplicationService service =
        new RagApplicationService(
            repo,
            embed,
            new NoOpReranker(),
            (TenantProvider) () -> TENANT,
            new NoDocAcl(),
            (RagEventSink) event -> {},
            30,
            0.0,
            0,
            -1,
            new GroundingGuard(0.6, 0.0),
            SensitiveDataMasker.disabled());

    List<GoldenCase> cases =
        List.of(
            new GoldenCase("逾期付款违约金", Set.of("1")),
            new GoldenCase("保密期限", Set.of("2")),
            new GoldenCase("合同解除恢复原状", Set.of("3")),
            new GoldenCase("不可抗力", Set.of("4")));

    List<String> failures = new ArrayList<>();
    double recallSum = 0.0;
    for (GoldenCase gc : cases) {
      List<String> got =
          service.retrieve(new RagQuery(gc.query(), null, TOP_K)).results().stream()
              .map(RagResult::chunkRef)
              .collect(Collectors.toList());
      long hit = gc.expected().stream().filter(got::contains).count();
      double recall = 1.0;
      if (!gc.expected().isEmpty()) {
        recall = hit / (double) gc.expected().size();
      }
      recallSum += recall;
      if (recall < 1.0) {
        failures.add("query='" + gc.query() + "' expected=" + gc.expected() + " got=" + got);
      }
    }
    double meanRecall = recallSum / cases.size();

    // 每个金标准用例的预期切片都必须落在 topK 内；聚合门槛与 sidecar 默认 gate 0.8 对齐并留余量
    assertThat(failures).as("以下用例未命中 topK：%s", failures).isEmpty();
    assertThat(meanRecall).as("mean context recall 低于门禁").isGreaterThanOrEqualTo(0.75);
  }

  /** 小型中文语料（6 切片，覆盖 4 个金标准主题）。 */
  private static List<RagChunk> corpus(TransitionalHashEmbedding embed) {
    record Raw(long id, String docId, String text, String clauseRef) {}

    List<Raw> raws =
        List.of(
            new Raw(1, "doc-1", "逾期付款应支付违约金", "clause-1"),
            new Raw(2, "doc-1", "保密期限为五年", "clause-2"),
            new Raw(3, "doc-1", "合同解除后恢复原状", "clause-3"),
            new Raw(4, "doc-2", "不可抗力免责", "clause-4"),
            new Raw(5, "doc-2", "争议提交仲裁", "clause-5"),
            new Raw(6, "doc-2", "合同签字盖章生效", "clause-6"));

    List<RagChunk> chunks = new ArrayList<>(raws.size());
    for (Raw r : raws) {
      chunks.add(
          RagChunk.of(
              r.id(),
              TENANT,
              r.docId(),
              DOC_VERSION,
              "S" + r.id(),
              r.text(),
              embed.embed(r.text()),
              EMBEDDING_VER,
              r.clauseRef(),
              0,
              r.text().length()));
    }
    return chunks;
  }
}
