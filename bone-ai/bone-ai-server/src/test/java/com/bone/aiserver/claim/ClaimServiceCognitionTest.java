package com.bone.aiserver.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * v1.3 AI 原生：POC 主机 {@link ClaimService#underwrite} 结构化认知输出验证。
 *
 * <p>重点（与产线 {@code AiGatewayPort.AiAssessment} 契约对齐）：①感知层从解析 IR 确定性派生 {@code clauseRefs} / {@code
 * evidenceRefs} / {@code facts}；②模型 / 提示词版本自报（未自报标 {@code local-stub}，防误读）；③LLM
 * 结构化认知按白名单收敛；④短路与转人工路径认知随之进入决策轨迹（轨迹非空）。
 */
class ClaimServiceCognitionTest {

  private static final String SOURCE_REF = "policy.pdf";
  private static final String DOC_CONTENT = "第一条 保险责任：承担住院医疗费用。 第二条 责任免除：既往症不赔。";

  private OrchestrationApplicationService orchestration;
  private DocumentParser parser;
  private HitlApplicationService hitlService;
  private ClaimService service;

  @BeforeEach
  void setUp() {
    orchestration = mock(OrchestrationApplicationService.class);
    parser = mock(DocumentParser.class);
    // 多条款单证：ClauseSplitter 切出两条条款（第一条 / 第二条），供感知层确定性派生
    when(parser.parse(any(), any()))
        .thenReturn(ParsedDocument.of(1, List.of(DOC_CONTENT), "pdfbox"));

    HitlItemRepository hitlRepo = mock(HitlItemRepository.class);
    HitlDispositionRepository dispositionRepo = mock(HitlDispositionRepository.class);
    hitlService = new HitlApplicationService(hitlRepo, dispositionRepo);

    service = new ClaimService(orchestration, parser, hitlService); // 未接入额度内核
  }

  private void stub(OrchestrationResult result) {
    when(orchestration.run(any())).thenReturn(result);
  }

  private ClaimService.ClaimDecision underwrite(double confidence, String riskLevel) {
    return service.underwrite(
        new ClaimService.ClaimRequest(
            "1",
            "C1",
            "门诊报销",
            3000.0,
            riskLevel,
            new ByteArrayInputStream(new byte[0]),
            SOURCE_REF));
  }

  private static LlmResponse llm(String model, String content) {
    return new LlmResponse(content, model, "stop", LlmUsage.empty());
  }

  @Test
  void perception_layer_derives_structured_cognition_from_parsed_ir() {
    stub(
        new OrchestrationResult(
            List.of(), false, null, llm("claim-assessor", "{\"confidence\":0.92}")));

    ClaimService.ClaimDecision d = underwrite(0.92, "LOW");

    assertThat(d.mode()).isEqualTo("LLM_EVALUATED");
    // 感知层：条款引用 / 证据定位 / 结构化事实 均非空且带稳定引用前缀
    assertThat(d.clauseRefs()).isNotEmpty();
    assertThat(d.clauseRefs()).allMatch(r -> r.startsWith(SOURCE_REF + "#clause-"));
    assertThat(d.evidenceRefs()).isNotEmpty();
    assertThat(d.evidenceRefs()).allMatch(r -> r.startsWith(SOURCE_REF + "#p1:"));
    assertThat(d.facts()).isNotEmpty();
    assertThat(d.facts()).allMatch(f -> !f.code().isBlank() && !f.value().isBlank());
    // 溯源：模型自报 + 提示词契约版本
    assertThat(d.modelVersion()).isEqualTo("claim-assessor");
    assertThat(d.promptVersion()).isEqualTo("claim-assess-local-v1");
  }

  @Test
  void model_version_falls_back_to_local_stub_when_not_reported() {
    // 模型未自报（null）→ 标 local-stub，防误读为已接入真实模型
    stub(new OrchestrationResult(List.of(), false, null, llm(null, "{\"confidence\":0.92}")));

    ClaimService.ClaimDecision d = underwrite(0.92, "LOW");

    assertThat(d.modelVersion()).isEqualTo("local-stub");
  }

  @Test
  void llm_supplied_discrepancies_are_whitelisted_and_non_compliant_codes_dropped() {
    stub(
        new OrchestrationResult(
            List.of(),
            false,
            null,
            llm(
                "claim-assessor",
                "{\"confidence\":0.92,\"discrepancies\":[\"AMOUNT_MISMATCH\",\"BOGUS_CODE\"]}")));

    ClaimService.ClaimDecision d = underwrite(0.92, "LOW");

    assertThat(d.discrepancies()).containsExactly("AMOUNT_MISMATCH");
  }

  @Test
  void short_circuit_path_still_carries_perception_cognition() {
    // 规则短路：llmResponse 为 null，但仍应带感知层认知（轨迹非空，而非空认知）
    stub(new OrchestrationResult(List.of(), true, "R001", null));

    ClaimService.ClaimDecision d = underwrite(0.0, "LOW");

    assertThat(d.mode()).isEqualTo("SHORT_CIRCUITED");
    assertThat(d.clauseRefs()).isNotEmpty();
    assertThat(d.evidenceRefs()).isNotEmpty();
    assertThat(d.facts()).isNotEmpty();
    assertThat(d.modelVersion()).isEqualTo("rule-short-circuit");
  }

  @Test
  void low_confidence_hitl_still_propagates_cognition() {
    // 低置信度（<0.7）→ 转人工，但认知字段随决策轨迹一起进入 HITL 待决
    stub(
        new OrchestrationResult(
            List.of(), false, null, llm("claim-assessor", "{\"confidence\":0.5}")));

    ClaimService.ClaimDecision d = underwrite(0.5, "LOW");

    assertThat(d.mode()).isEqualTo("HITL_PENDING");
    assertThat(d.reasonCode()).isEqualTo("CONFIDENCE_LOW");
    assertThat(d.facts()).isNotEmpty();
    assertThat(d.clauseRefs()).isNotEmpty();
  }
}
