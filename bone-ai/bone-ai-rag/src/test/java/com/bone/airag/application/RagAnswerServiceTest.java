package com.bone.airag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.AnswerGenerationException;
import com.bone.airag.domain.gateway.AnswerGenerationPort;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.GroundingJudgePort;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.GroundingJudgeVerdict;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagEvent;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.GroundedPromptBuilder;
import com.bone.airag.domain.service.GroundingGuard;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpGroundingJudge;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.domain.service.SensitiveDataMasker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R1 闭环回归：检索 → 接地提示词 → 生成 → 守卫 → 拒答或返回；R5 叠加语义裁判三态。 */
class RagAnswerServiceTest {

  private static final long TENANT = 42L;
  private static final String CLAUSE_A = "甲方逾期付款的，每逾期一日按未付金额千分之一支付违约金";
  private static final String CLAUSE_B = "保密期限自合同生效之日起五年";

  /** 生成桩：回放固定答案并记录提示词，用于断言「无据时根本没调模型」。 */
  private static final class StubAnswerPort implements AnswerGenerationPort {
    private final String answer;
    private final List<String> prompts = new ArrayList<>();

    private StubAnswerPort(String answer) {
      this.answer = answer;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
      prompts.add(userPrompt);
      return answer;
    }
  }

  /** 生成桩（故障型）：模拟网关卡面治理拒绝 / provider 故障，用于断言失败收敛。 */
  private static final class ThrowingAnswerPort implements AnswerGenerationPort {

    private final RuntimeException failure;

    private ThrowingAnswerPort(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
      throw failure;
    }
  }

  private static RagChunk chunk(Long id, String text, String clauseRef) {
    return RagChunk.of(
        id,
        TENANT,
        "doc-1",
        "v1",
        "Clause " + id,
        text,
        new float[] {1f, 0f},
        "hash-64",
        clauseRef,
        0,
        10);
  }

  /** 裁判桩（R5）：按脚本返回判定，并记录入参——断言裁判收到「问题 / 答案 / included 上下文」三元组。 */
  private static final class StubGroundingJudge implements GroundingJudgePort {

    private final GroundingJudgeVerdict verdict;
    private final List<String> questions = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private final List<List<RagResult>> contexts = new ArrayList<>();

    private StubGroundingJudge(GroundingJudgeVerdict verdict) {
      this.verdict = verdict;
    }

    @Override
    public GroundingJudgeVerdict judge(String question, String answer, List<RagResult> ctxs) {
      questions.add(question);
      answers.add(answer);
      contexts.add(ctxs);
      return verdict;
    }
  }

  /** 事件收集桩：断言合规拒答事件已下沉（监控可聚合）。 */
  private static final class CapturingEventSink implements RagEventSink {

    private final List<RagEvent> events = new ArrayList<>();

    @Override
    public void record(RagEvent event) {
      events.add(event);
    }
  }

  private static RagAnswerService service(
      List<RagChunk> corpus, AnswerGenerationPort port, int budget) {
    return service(corpus, port, budget, new NoOpGroundingJudge(), event -> {});
  }

  private static RagAnswerService service(
      List<RagChunk> corpus,
      AnswerGenerationPort port,
      int budget,
      GroundingJudgePort judge,
      RagEventSink eventSink) {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);
    when(tenant.currentTenantId()).thenReturn(TENANT);
    when(repo.findByTenantAndDocScope(eq(TENANT), any())).thenReturn(corpus);
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed(any())).thenReturn(new float[] {1f, 0f});
    RagApplicationService rag =
        new RagApplicationService(
            repo,
            embed,
            new NoOpReranker(),
            tenant,
            new NoDocAcl(),
            (RagEventSink) event -> {},
            30,
            0.0,
            0,
            -1,
            new GroundingGuard(0.6, 0.0),
            SensitiveDataMasker.disabled(),
            false);
    return new RagAnswerService(rag, port, new GroundedPromptBuilder(budget), judge, eventSink);
  }

  @Test
  void answers_when_output_is_grounded_in_context() {
    StubAnswerPort port = new StubAnswerPort(CLAUSE_A + "[1]。");
    RagAnswerService service = service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.answered()).isTrue();
    assertThat(out.errorCode()).isNull();
    assertThat(out.answer()).isEqualTo(CLAUSE_A + "[1]。");
    assertThat(out.contexts()).hasSize(1);
    // 提示词必须是围栏包裹 + 编号上下文，而不是裸拼原文
    assertThat(port.prompts).hasSize(1);
    assertThat(port.prompts.get(0))
        .contains(GroundedPromptBuilder.DATA_FENCE_OPEN)
        .contains("[1] 出处: clause-1");
  }

  @Test
  void refuses_when_output_contains_fabricated_number() {
    String fabricated = "甲方逾期付款的，每逾期一日按未付金额3%支付违约金[1]。";
    StubAnswerPort port = new StubAnswerPort(fabricated);
    RagAnswerService service = service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.answered()).isFalse();
    assertThat(out.errorCode()).isEqualTo(AiRagErrorCodes.GROUNDING_FAILED);
    assertThat(out.answer()).isEqualTo(RagAnswerService.REFUSAL_ANSWER);
    assertThat(out.verdict().reason()).isEqualTo(GroundingGuard.REASON_UNSUPPORTED_CLAIM);
  }

  @Test
  void skips_llm_entirely_when_retrieval_is_empty() {
    StubAnswerPort port = new StubAnswerPort("任意答案");
    RagAnswerService service = service(List.of(), port, 0);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    // 无上下文却调模型 ⇒ 必然靠模型自身知识编造；这条红线必须在调用前拦住
    assertThat(port.prompts).isEmpty();
    assertThat(out.answered()).isFalse();
    assertThat(out.verdict().reason()).isEqualTo(GroundingGuard.REASON_NO_CONTEXT);
    assertThat(out.contexts()).isEmpty();
  }

  @Test
  void validates_against_included_contexts_only_after_budget_truncation() {
    int budget = GroundedPromptBuilder.estimateTokens(CLAUSE_A);
    // 只容得下第一条，模型却引用了被截掉的 [2]——守卫必须按 included 判为幻觉引用
    StubAnswerPort port = new StubAnswerPort("保密期限为五年[2]。");
    RagAnswerService service =
        service(
            List.of(chunk(1L, CLAUSE_A, "clause-1"), chunk(2L, CLAUSE_B, "clause-2")),
            port,
            budget);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.truncated()).isTrue();
    assertThat(out.degradations()).contains(RagAnswerService.DEGRADE_CONTEXT_TRUNCATED);
    assertThat(out.contexts()).hasSize(1);
    assertThat(out.verdict().reason()).isEqualTo(GroundingGuard.REASON_INVALID_CITATION);
    assertThat(out.verdict().invalidCitations()).containsExactly("[2]");
  }

  @Test
  void converges_governance_rejection_into_refusal_with_original_error_code() {
    // 网关治理拒绝（此处模拟注入疑似）：不得以异常冲出编排边界，且 errorCode 必须保真透传——
    // 把注入拒绝归一成「服务不可用」会让安全事件从监控里消失
    ThrowingAnswerPort port =
        new ThrowingAnswerPort(
            new AnswerGenerationException(400, "疑似提示注入", "AI_GATEWAY_INJECTION_SUSPECTED"));
    RagAnswerService service = service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.answered()).isFalse();
    assertThat(out.errorCode()).isEqualTo("AI_GATEWAY_INJECTION_SUSPECTED");
    assertThat(out.answer()).isEqualTo(RagAnswerService.REFUSAL_ANSWER);
    assertThat(out.verdict().reason()).isEqualTo(GroundingGuard.REASON_GENERATION_UNAVAILABLE);
    // 故障明细落在审计轨迹位（unsupportedSentences），供定位
    assertThat(out.verdict().unsupportedSentences()).containsExactly("疑似提示注入");
  }

  @Test
  void converges_unknown_generation_failure_into_unavailable() {
    // 端口实现的未知运行时故障（provider 超时等）：同样收敛为可控拒答，errorCode 归一为「生成不可用」
    ThrowingAnswerPort port = new ThrowingAnswerPort(new IllegalStateException("provider timeout"));
    RagAnswerService service = service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.answered()).isFalse();
    assertThat(out.errorCode()).isEqualTo(AiRagErrorCodes.ANSWER_GENERATION_UNAVAILABLE);
    assertThat(out.answer()).isEqualTo(RagAnswerService.REFUSAL_ANSWER);
    assertThat(out.verdict().unsupportedSentences())
        .anyMatch(detail -> detail.contains("provider timeout"));
  }

  @Test
  void refuses_when_semantic_judge_rejects_paraphrase_fabrication() {
    // 词面守卫已过（逐句支撑 + 数字一致），但语义裁判判定存在无支撑陈述（拼接句编造）——
    // 必须拒答且 judgeVerdict 透出供审计；与词面拒答分码（GROUNDING_JUDGE_REJECTED），监控可分别聚合
    StubAnswerPort port = new StubAnswerPort(CLAUSE_A + "[1]。");
    StubGroundingJudge judge =
        new StubGroundingJudge(
            new GroundingJudgeVerdict(
                false, GroundingJudgeVerdict.REASON_UNSUPPORTED, List.of("甲方还应赔偿乙方全部间接损失")));
    CapturingEventSink sink = new CapturingEventSink();
    RagAnswerService service =
        service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0, judge, sink);

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.answered()).isFalse();
    assertThat(out.errorCode()).isEqualTo(AiRagErrorCodes.GROUNDING_FAILED);
    assertThat(out.answer()).isEqualTo(RagAnswerService.REFUSAL_ANSWER);
    assertThat(out.judgeVerdict().reason()).isEqualTo(GroundingJudgeVerdict.REASON_UNSUPPORTED);
    assertThat(out.judgeVerdict().unsupportedSentences()).containsExactly("甲方还应赔偿乙方全部间接损失");
    // 裁判拒答是合规结论不是降级：degradations 不得出现 JUDGE_DEGRADED
    assertThat(out.degradations()).doesNotContain(RagAnswerService.DEGRADE_GROUNDING_JUDGE);
    assertThat(sink.events)
        .extracting(RagEvent::type)
        .contains(RagAnswerService.EVENT_GROUNDING_JUDGE_REJECTED);
  }

  @Test
  void degrades_to_deterministic_only_when_judge_unavailable() {
    // 裁判自身故障（provider 不可用 / 治理拒绝 / 输出不可解析）：不得据此拒答（自造可用性事故），
    // 也不得静默放行——回落确定性单层校验 + GROUNDING_JUDGE_DEGRADED 痕迹（「只有一层在岗」可观测）
    StubAnswerPort port = new StubAnswerPort(CLAUSE_A + "[1]。");
    StubGroundingJudge judge =
        new StubGroundingJudge(
            new GroundingJudgeVerdict(
                false, GroundingJudgeVerdict.REASON_UNAVAILABLE, List.of("provider unavailable")));
    RagAnswerService service =
        service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0, judge, event -> {});

    RagAnswerOutcome out = service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(out.answered()).isTrue();
    assertThat(out.answer()).isEqualTo(CLAUSE_A + "[1]。");
    assertThat(out.degradations()).contains(RagAnswerService.DEGRADE_GROUNDING_JUDGE);
    assertThat(out.judgeVerdict().unavailable()).isTrue();
  }

  @Test
  void semantic_judge_receives_question_answer_and_included_contexts() {
    // 裁判必须拿到「问题 / 答案 / 真正进提示词的上下文」：缺问题则裁判无法判断「答的是什么」，
    // 拿全部检索结果则与被预算截断的提示词对不上账（与守卫的 included 口径同源）
    StubAnswerPort port = new StubAnswerPort(CLAUSE_A + "[1]。");
    StubGroundingJudge judge =
        new StubGroundingJudge(
            new GroundingJudgeVerdict(true, GroundingJudgeVerdict.REASON_SUPPORTED, List.of()));
    RagAnswerService service =
        service(List.of(chunk(1L, CLAUSE_A, "clause-1")), port, 0, judge, event -> {});

    service.answer(new RagQuery("逾期付款违约金", null, 5));

    assertThat(judge.questions).containsExactly("逾期付款违约金");
    assertThat(judge.answers).containsExactly(CLAUSE_A + "[1]。");
    assertThat(judge.contexts).hasSize(1);
    assertThat(judge.contexts.get(0)).hasSize(1);
    assertThat(judge.contexts.get(0).get(0).clauseRef()).isEqualTo("clause-1");
  }
}
