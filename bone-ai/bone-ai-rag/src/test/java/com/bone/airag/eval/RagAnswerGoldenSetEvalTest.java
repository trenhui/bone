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
import com.bone.airag.domain.service.AnswerQuality;
import com.bone.airag.domain.service.GroundingGuard;
import com.bone.airag.domain.service.HeuristicAnswerQualityJudge;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.domain.service.SensitiveDataMasker;
import com.bone.airag.infrastructure.embedding.TransitionalHashEmbedding;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RAG 回答质量 golden-set 门禁（缺口③「faithfulness 语义级评测」的确定性一层，随 {@code mvn clean verify} 强制）。
 *
 * <p>与 {@link RagGoldenSetEvalTest} 的分工：后者守<b>检索</b>质量（预期切片落 topK）；本测试守<b>回答</b>质量——
 * 用同一受控检索链取回上下文，再以 {@link HeuristicAnswerQualityJudge} 判「据作答」：任何使检索漂移 （预期切片掉出 topK / rerank 误伤 /
 * 阈值误杀）或使答案脱离上下文的回归都会拉低 faithfulness 而红灯。
 *
 * <p>含<b>阴性对照</b>（幻觉答案必须不过门禁）——没有阴性对照的门禁无法证明自己有效。
 */
class RagAnswerGoldenSetEvalTest {

  private static final long TENANT = 1L;
  private static final String DOC_VERSION = "v1";
  private static final String EMBEDDING_VER = "hash-64-cjk";
  private static final int TOP_K = 3;

  /** 忠实度门禁（fixture 答案均为据实表述，实测 1.0，留 0.25 余量防分词波动）。 */
  private static final double FAITHFULNESS_GATE = 0.75;

  /** 相关性门禁（fixture 答案均含查询主题词）。 */
  private static final double RELEVANCE_GATE = 0.5;

  private final HeuristicAnswerQualityJudge judge = new HeuristicAnswerQualityJudge();

  private record AnswerCase(String query, String answer, String note) {}

  private final List<AnswerCase> cases =
      List.of(
          new AnswerCase("逾期付款违约金", "逾期付款应当支付违约金。", "据 chunk-1 的扩展表述"),
          new AnswerCase("保密期限", "本合同保密期限为五年。", "据 chunk-2 的扩展表述"),
          new AnswerCase("合同解除恢复原状", "合同解除后双方恢复原状。", "据 chunk-3 的扩展表述"),
          new AnswerCase("不可抗力", "因不可抗力导致的损失免责。", "据 chunk-4 的扩展表述"));

  @Test
  @DisplayName("golden-set 回答门禁：检索 + 判官端到端，忠实度/相关性双达标")
  void golden_set_answer_quality_gate() {
    TransitionalHashEmbedding embed = new TransitionalHashEmbedding();
    List<RagChunk> corpus = corpus(embed);

    ChunkRepository repo = mock(ChunkRepository.class);
    when(repo.findByTenantAndDocScope(eq(TENANT), any())).thenReturn(corpus);

    RagApplicationService service = service(repo, embed);

    List<String> failures = new ArrayList<>();
    for (AnswerCase ac : cases) {
      List<String> contexts =
          service.retrieve(new RagQuery(ac.query(), null, TOP_K)).results().stream()
              .map(RagResult::text)
              .collect(Collectors.toList());
      AnswerQuality quality = judge.judge(ac.query(), contexts, ac.answer());

      if (quality.faithfulness() < FAITHFULNESS_GATE
          || quality.answerRelevance() < RELEVANCE_GATE
          || quality.contextPrecision() <= 0.0) {
        failures.add(
            String.format(
                "query='%s' faithfulness=%.2f relevance=%.2f precision=%.2f unsupported=%s",
                ac.query(),
                quality.faithfulness(),
                quality.answerRelevance(),
                quality.contextPrecision(),
                quality.unsupportedClaims()));
      }
    }

    assertThat(failures).as("以下回答用例未过质量门禁：%s", failures).isEmpty();
  }

  @Test
  @DisplayName("阴性对照：幻觉答案必须被门禁拦下（否则门禁无效）")
  void hallucinated_answer_must_fail_the_gate() {
    TransitionalHashEmbedding embed = new TransitionalHashEmbedding();
    List<RagChunk> corpus = corpus(embed);

    ChunkRepository repo = mock(ChunkRepository.class);
    when(repo.findByTenantAndDocScope(eq(TENANT), any())).thenReturn(corpus);

    RagApplicationService service = service(repo, embed);

    List<String> contexts =
        service.retrieve(new RagQuery("逾期付款违约金", null, TOP_K)).results().stream()
            .map(RagResult::text)
            .collect(Collectors.toList());

    // 话题漂移幻觉：与任何检索结果均无词汇级支撑
    AnswerQuality hallucinated = judge.judge("逾期付款违约金", contexts, "本合同适用联合国海洋法公约，争议由海牙法院管辖。");

    assertThat(hallucinated.faithfulness())
        .as("幻觉答案的忠实度必须低于门禁，否则门禁形同虚设")
        .isLessThan(FAITHFULNESS_GATE);
    assertThat(hallucinated.meetsFaithfulnessGate(FAITHFULNESS_GATE)).isFalse();

    // 半据半编：部分忠实同样必须被拦（灰度放行判据依赖部分忠实可检出）
    AnswerQuality mixed = judge.judge("逾期付款违约金", contexts, "逾期付款应支付违约金。本合同适用联合国海洋法公约。");

    assertThat(mixed.faithfulness()).isLessThan(FAITHFULNESS_GATE);
    assertThat(mixed.unsupportedClaims()).containsExactly("本合同适用联合国海洋法公约");
  }

  private static RagApplicationService service(
      ChunkRepository repo, TransitionalHashEmbedding embed) {
    return new RagApplicationService(
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
        SensitiveDataMasker.disabled(),
        false);
  }

  /** 与检索 golden-set 同源的小型中文语料（6 切片，覆盖 4 个主题）。 */
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
