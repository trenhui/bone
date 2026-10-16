package com.bone.aiserver.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.infrastructure.MockLlmClient;
import com.bone.aidoc.infrastructure.parser.PdfBoxDocumentParser;
import com.bone.aiorch.application.HitlService;
import com.bone.aiorch.application.OrchestrationService;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.airules.application.RuleQueryService;
import com.bone.airules.application.RuleService;
import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import com.bone.aitrace.application.EvidenceQueryService;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.aitrace.infrastructure.event.EvidenceTraceListener;
import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 智能理赔场景端到端验证（覆盖全部 AI 能力：document / rules / gateway / trace / orchestration / HITL）。
 *
 * <p>真实组合服务（QlExpressRuleEngine / PdfBoxDocumentParser / MockLlmClient / 进程内事件总线）+ mock 仓储，不
 * boot、不连库。 三走查覆盖理赔闭环的三种结局：
 *
 * <ul>
 *   <li><b>A 免责短路</b>：理赔触发免责规则 ⇒ 不调 LLM、不发事件、不留痕（成本红线结构强制）；
 *   <li><b>B 常规核赔</b>：规则未命中 ⇒ 调 LLM ⇒ 证据链（LlmCallRecorded + LlmUsageRecorded）⇒ trace 落快照可回放；
 *   <li><b>C 高风险人工终审</b>：风险 HIGH ⇒ HITL 五态流转（confirm/reject/modify + ReasonType 回流 + 处置链可回放）。
 * </ul>
 */
class ClaimSettlementScenarioTest {

  private RuleAssetRepository ruleRepo;
  private EvidenceSnapshotRepository evidenceRepo;
  private HitlItemRepository hitlRepo;
  private ClaimService claimService;
  private final List<DomainEvent> published = new ArrayList<>();
  private RuleService ruleService;
  private RuleQueryService ruleQueryService;

  @BeforeEach
  void setUp() {
    ruleRepo = mock(RuleAssetRepository.class);
    evidenceRepo = mock(EvidenceSnapshotRepository.class);
    hitlRepo = mock(HitlItemRepository.class);

    RuleEngine ruleEngine = new QlExpressRuleEngine();
    ruleService = new RuleService(ruleRepo, ruleEngine);
    ruleQueryService = new RuleQueryService(ruleRepo);

    final EvidenceTraceListener listener =
        new EvidenceTraceListener(evidenceRepo, (TenantProvider) () -> 42L);
    EvidenceQueryService evidenceQuery = new EvidenceQueryService(evidenceRepo);

    // 进程内事件总线：gateway 发布 → 派发给 trace 监听
    DomainEventPublisher bus =
        new DomainEventPublisher() {
          @Override
          public void publish(DomainEvent event) {
            published.add(event);
            if (event instanceof LlmCallRecorded recorded) {
              listener.onLlmCallRecorded(recorded);
            }
          }

          @Override
          public void publishAll(Collection<? extends DomainEvent> events) {
            events.forEach(this::publish);
          }
        };

    LlmClient llmClient = new MockLlmClient();
    LlmGatewayService gateway = new LlmGatewayService(llmClient, bus);
    OrchestrationService orchestration =
        new OrchestrationService(ruleService, ruleQueryService, gateway);
    HitlService hitlService = new HitlService(hitlRepo);
    claimService = new ClaimService(orchestration, new PdfBoxDocumentParser(), hitlService);
  }

  // ===== 走查 A：免责条款短路（document + rules + orchestration + gateway 成本红线） =====

  @Test
  void walkthroughA_exclusion_rule_short_circuits_and_parses_policy_ir() throws IOException {
    // 免责条款：金额≥5 万直接拦截（既往症/大额免责）
    RuleAsset exclusion = RuleAsset.create("t1", "EXCLUDE", "免责拦截", "金额≥5万");
    exclusion.setId("ex-1");
    exclusion.publishFirst("amount >= 50000", true, 0, "ops"); // hitBlock=true
    when(ruleRepo.findActive("t1")).thenReturn(List.of(exclusion));

    // 理赔条款 PDF：含免责条款
    byte[] policyPdf =
        policyPdf(
            "Clause 1: Insurance Coverage\nCovers medical expenses.\nClause 2: Exclusions\nPre-existing conditions and high amounts are not covered.\n");

    ClaimService.ClaimDecision decision =
        claimService.underwrite(
            new ClaimService.ClaimRequest(
                "t1",
                "claim-A",
                "住院医疗费报销",
                80000.0,
                "LOW",
                new ByteArrayInputStream(policyPdf),
                "policy-A.pdf"));

    // document：IR 已解析出条款切片 + span
    assertThat(decision.ir()).isNotNull();
    assertThat(decision.ir().clauses()).isNotEmpty();
    assertThat(decision.ir().clauses().get(0).clauseNo()).startsWith("Clause 1");

    // rules + orchestration：免责命中短路
    assertThat(decision.mode()).isEqualTo("SHORT_CIRCUITED");
    assertThat(decision.verdict()).isEqualTo("AUTO_REJECT");
    assertThat(decision.orchestration().shortCircuited()).isTrue();
    assertThat(decision.orchestration().blockedRuleCode()).isEqualTo("EXCLUDE");

    // gateway 成本红线：不调 LLM ⇒ 无事件、无留痕
    assertThat(published).isEmpty();
    verify(evidenceRepo, never()).insert(any());
  }

  // ===== 走查 B：常规核赔（orchestration + gateway 计量 + trace 证据链） =====

  @Test
  void walkthroughB_normal_claim_calls_llm_trace_persists_and_auto_approves() throws IOException {
    // 风控规则命中不短路（仅拦截大额，常规金额不触发）
    RuleAsset risk = RuleAsset.create("t1", "RISK", "风控", "金额≥5万");
    risk.setId("risk-1");
    risk.publishFirst("amount >= 50000", false, 0, "ops"); // hitBlock=false
    when(ruleRepo.findActive("t1")).thenReturn(List.of(risk));

    byte[] policyPdf = policyPdf("Clause 1: Insurance Coverage\nCovers medical expenses.\n");

    ClaimService.ClaimDecision decision =
        claimService.underwrite(
            new ClaimService.ClaimRequest(
                "t1",
                "claim-B",
                "门诊报销",
                2000.0,
                "LOW",
                new ByteArrayInputStream(policyPdf),
                "policy-B.pdf"));

    // 常规金额未命中拦截 ⇒ LLM 评估 ⇒ 自动通过
    assertThat(decision.mode()).isEqualTo("LLM_EVALUATED");
    assertThat(decision.verdict()).isEqualTo("AUTO_APPROVE");
    assertThat(decision.orchestration().llmResponse()).isNotNull();

    // gateway 发布计量 + 证据事件
    assertThat(published).anyMatch(e -> e instanceof LlmCallRecorded);
    assertThat(published).anyMatch(e -> e instanceof LlmUsageRecorded);

    // trace：监听落证据快照（LLM_CALL + 租户 + 哈希三元组），可回放
    ArgumentCaptor<EvidenceSnapshot> captor = ArgumentCaptor.forClass(EvidenceSnapshot.class);
    verify(evidenceRepo).insert(captor.capture());
    EvidenceSnapshot saved = captor.getValue();
    assertThat(saved.type()).isEqualTo(EvidenceType.LLM_CALL);
    assertThat(saved.getTenantId()).isEqualTo(42L);
    assertThat(saved.getTraceId()).isNotNull();
    assertThat(saved.getEngineVersion()).isNotBlank(); // 哈希三元组含引擎版本
  }

  // ===== 走查 C：高风险转 HITL 人工终审（HITL 五态 + ReasonType + 处置链） =====

  @Test
  void walkthroughC_high_risk_claim_escalates_to_hitl_and_disposition_replays() throws IOException {
    RuleAsset risk = RuleAsset.create("t1", "RISK", "风控", "金额≥5万");
    risk.setId("risk-1");
    risk.publishFirst("amount >= 50000", false, 0, "ops");
    when(ruleRepo.findActive("t1")).thenReturn(List.of(risk));

    byte[] policyPdf = policyPdf("Clause 1: Insurance Coverage\nCovers medical expenses.\n");

    ClaimService.ClaimRequest request =
        new ClaimService.ClaimRequest(
            "t1",
            "claim-C",
            "大额手术报销",
            30000.0,
            "HIGH",
            new ByteArrayInputStream(policyPdf),
            "policy-C.pdf");

    // 预置 hitl item：让 claimService.act / dispositionHistory 能取到
    HitlItem pendingItem = HitlItem.create("t1", "claim-claim-C", "建议核赔", 30000.0, "HIGH", 0.9);
    pendingItem.setId("hitl-C");
    when(hitlRepo.findPendingById("t1", "hitl-C")).thenReturn(java.util.Optional.of(pendingItem));
    when(hitlRepo.findById("hitl-C")).thenReturn(pendingItem);

    ClaimService.ClaimDecision decision = claimService.underwrite(request);

    // 高风险 ⇒ 强制 HITL 待人工，平台不擅自决策
    assertThat(decision.mode()).isEqualTo("HITL_PENDING");
    assertThat(decision.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(decision.hitlItemId()).isNotBlank();
    assertThat(decision.hitlStatus()).isEqualTo("PENDING");

    // 人工驳回（必带 ReasonType）→ REJECTED + 处置链可回放
    ClaimService.ClaimDecision finalDecision = decision;
    HitlItem.Disposition disposition =
        claimService.act("t1", "hitl-C", "ops-1", "reject", null, ReasonType.RULE_MISS, "既往症漏判");
    assertThat(disposition.action()).isEqualTo("reject");
    assertThat(disposition.reasonType()).isEqualTo("RULE_MISS");
    assertThat(pendingItem.getStatus()).isEqualTo(HitlStatus.REJECTED);

    // 处置链回放：谁在何时做了什么裁决、带什么原因
    var history = claimService.dispositionHistory("t1", "hitl-C");
    assertThat(history).hasSize(1);
    assertThat(history.get(0).actor()).isEqualTo("ops-1");
    assertThat(history.get(0).comment()).isEqualTo("既往症漏判");
    assertThat(finalDecision.claimId()).isEqualTo("claim-C");
  }

  /** 生成一页理赔条款 PDF（PDFBox 自举：写入 → 解析链验证）。 */
  private static byte[] policyPdf(String text) throws IOException {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(50, 700);
        int line = 0;
        for (String s : text.split("\n")) {
          if (line > 0) {
            cs.newLineAtOffset(0, -16);
          }
          cs.showText(s);
          line++;
        }
        cs.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }
}
