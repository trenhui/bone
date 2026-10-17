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
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.domain.hitl.HitlDisposition;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import com.bone.aitrace.application.EvidenceQueryApplicationService;
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
  private RuleAssetVersionRepository ruleVersionRepo;
  private EvidenceSnapshotRepository evidenceRepo;
  private HitlItemRepository hitlRepo;
  private HitlDispositionRepository dispositionRepo;
  private final List<HitlDisposition> dispositions = new ArrayList<>();
  private ClaimService claimService;
  private final List<DomainEvent> published = new ArrayList<>();
  private RuleApplicationService ruleService;
  private RuleQueryApplicationService ruleQueryService;

  @BeforeEach
  void setUp() {
    ruleRepo = mock(RuleAssetRepository.class);
    ruleVersionRepo = mock(RuleAssetVersionRepository.class);
    evidenceRepo = mock(EvidenceSnapshotRepository.class);
    hitlRepo = mock(HitlItemRepository.class);
    dispositionRepo = mock(HitlDispositionRepository.class);
    // 进程内假存储：insert 累积、findByItemId 按项过滤（模拟 hitl_disposition 表）
    when(dispositionRepo.insert(any()))
        .thenAnswer(
            inv -> {
              dispositions.add(inv.getArgument(0));
              return 1L;
            });
    when(dispositionRepo.findByItemId(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            inv ->
                dispositions.stream()
                    .filter(d -> d.getItemId().equals(inv.<String>getArgument(0)))
                    .toList());

    RuleEngine ruleEngine = new QlExpressRuleEngine();
    ruleService = new RuleApplicationService(ruleRepo, ruleVersionRepo, ruleEngine);
    ruleQueryService = new RuleQueryApplicationService(ruleRepo, ruleVersionRepo);

    final EvidenceTraceListener listener =
        new EvidenceTraceListener(evidenceRepo, (TenantProvider) () -> 42L);
    EvidenceQueryApplicationService evidenceQuery =
        new EvidenceQueryApplicationService(evidenceRepo);

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
    LlmGatewayService gateway =
        new LlmGatewayService(
            llmClient, bus, (com.bone.ai.gateway.domain.gateway.TenantProvider) () -> "42");
    OrchestrationApplicationService orchestration =
        new OrchestrationApplicationService(ruleService, ruleQueryService, gateway);
    HitlApplicationService hitlService = new HitlApplicationService(hitlRepo, dispositionRepo);
    claimService = new ClaimService(orchestration, new PdfBoxDocumentParser(), hitlService);
  }

  // ===== 走查 A：免责条款短路（document + rules + orchestration + gateway 成本红线） =====

  @Test
  void walkthroughA_exclusion_rule_short_circuits_and_parses_policy_ir() throws IOException {
    // 免责条款：金额≥5 万直接拦截（既往症/大额免责）
    RuleAsset exclusion = RuleAsset.create("1", "EXCLUDE", "免责拦截", "金额≥5万");
    exclusion.setId("ex-1");
    exclusion.publishFirst("amount >= 50000", true, 0, "ops"); // hitBlock=true
    when(ruleRepo.findActive("1")).thenReturn(List.of(exclusion));

    // 理赔条款 PDF：含免责条款
    byte[] policyPdf =
        policyPdf(
            "Clause 1: Insurance Coverage\nCovers medical expenses.\nClause 2: Exclusions\nPre-existing conditions and high amounts are not covered.\n");

    ClaimService.ClaimDecision decision =
        claimService.underwrite(
            new ClaimService.ClaimRequest(
                "1",
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

  // ===== 走查 B：常规核赔（orchestration + gateway 计量 + trace 证据链；无置信度 ⇒ fail-closed 转人工） =====

  @Test
  void walkthroughB_normal_claim_calls_llm_trace_persists_and_escalates_without_confidence()
      throws IOException {
    // 风控规则命中不短路（仅拦截大额，常规金额不触发）
    RuleAsset risk = RuleAsset.create("1", "RISK", "风控", "金额≥5万");
    risk.setId("risk-1");
    risk.publishFirst("amount >= 50000", false, 0, "ops"); // hitBlock=false
    when(ruleRepo.findActive("1")).thenReturn(List.of(risk));

    byte[] policyPdf = policyPdf("Clause 1: Insurance Coverage\nCovers medical expenses.\n");

    ClaimService.ClaimDecision decision =
        claimService.underwrite(
            new ClaimService.ClaimRequest(
                "1",
                "claim-B",
                "门诊报销",
                2000.0,
                "LOW",
                new ByteArrayInputStream(policyPdf),
                "policy-B.pdf"));

    // 常规金额未命中拦截 ⇒ LLM 评估；MockLlmClient 不携带置信度 ⇒ 缺省 0.0 强制人工（fail-closed，
    // 2026-10-17 修复：此前缺省 0.9 会「无置信度自动通过」——拿不出置信度就不该自动决策）
    assertThat(decision.mode()).isEqualTo("HITL_PENDING");
    assertThat(decision.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(decision.confidence()).isEqualTo(0.0);
    assertThat(decision.orchestration().llmResponse()).isNotNull();
    assertThat(decision.hitlItemId()).isNotBlank();

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

  // ===== 走查 C：低置信 / 高风险转 HITL 人工终审（HITL 五态 + ReasonType + 处置链可回放） =====

  @Test
  void walkthroughC_low_confidence_claim_escalates_to_hitl_and_disposition_replays()
      throws IOException {
    RuleAsset risk = RuleAsset.create("1", "RISK", "风控", "金额≥5万");
    risk.setId("risk-1");
    risk.publishFirst("amount >= 50000", false, 0, "ops");
    when(ruleRepo.findActive("1")).thenReturn(List.of(risk));

    byte[] policyPdf = policyPdf("Clause 1: Insurance Coverage\nCovers medical expenses.\n");

    // 低置信 LlmClient：content 携带 conf:0.5 → 触发 <0.7 强制人工（覆盖低置信 HITL 分支可达性）
    LlmClient lowConfidence =
        request ->
            new com.bone.ai.gateway.domain.LlmResponse(
                "conf:0.5 建议核赔",
                "claim-assessor",
                "stop",
                new com.bone.ai.gateway.domain.LlmUsage(10, 5, 15));

    // 低置信 client + mock hitlRepo 构造 ClaimService；裁决作用于 underwrite 真实产出项（捕获 save）
    DomainEventPublisher bus =
        new DomainEventPublisher() {
          @Override
          public void publish(DomainEvent event) {}

          @Override
          public void publishAll(Collection<? extends DomainEvent> events) {}
        };
    LlmGatewayService gateway =
        new LlmGatewayService(
            lowConfidence, bus, (com.bone.ai.gateway.domain.gateway.TenantProvider) () -> "42");
    OrchestrationApplicationService orch =
        new OrchestrationApplicationService(ruleService, ruleQueryService, gateway);
    HitlApplicationService hitlService = new HitlApplicationService(hitlRepo, dispositionRepo);
    ClaimService localService = new ClaimService(orch, new PdfBoxDocumentParser(), hitlService);

    ClaimService.ClaimRequest request =
        new ClaimService.ClaimRequest(
            "1",
            "claim-C",
            "手术报销",
            30000.0,
            "MEDIUM",
            new ByteArrayInputStream(policyPdf),
            "policy-C.pdf");

    ClaimService.ClaimDecision decision = localService.underwrite(request);

    // 低置信（0.5 < 0.7）⇒ 强制 HITL 待人工，平台不擅自决策
    assertThat(decision.mode()).isEqualTo("HITL_PENDING");
    assertThat(decision.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(decision.confidence()).isEqualTo(0.5);
    assertThat(decision.hitlItemId()).isNotBlank();
    assertThat(decision.hitlStatus()).isEqualTo("PENDING");

    // 捕获 underwrite 产出的 HITL item，使 act/dispositionHistory 按真实 itemId 取回——裁决与创建打通
    String itemId = decision.hitlItemId();
    HitlItem created = captureSavedItem();
    assertThat(created.getStatus()).isEqualTo(HitlStatus.PENDING);
    when(hitlRepo.findPendingById("1", itemId)).thenReturn(java.util.Optional.of(created));
    when(hitlRepo.findById(itemId)).thenReturn(created);

    HitlItem.Disposition disposition =
        localService.act("1", itemId, "ops-1", "reject", null, ReasonType.RULE_MISS, "既往症漏判");
    assertThat(disposition.action()).isEqualTo("reject");
    assertThat(disposition.reasonType()).isEqualTo("RULE_MISS");
    assertThat(created.getStatus()).isEqualTo(HitlStatus.REJECTED);

    // 处置链回放：谁在何时做了什么裁决、带什么原因
    var history = localService.dispositionHistory("1", itemId);
    assertThat(history).hasSize(1);
    assertThat(history.get(0).actor()).isEqualTo("ops-1");
    assertThat(history.get(0).comment()).isEqualTo("既往症漏判");
    assertThat(decision.claimId()).isEqualTo("claim-C");
  }

  /** 捕获 underwrite 触发 hitlRepo.save 的 HITL item（使 act 可作用于真实产出项）。 */
  private HitlItem captureSavedItem() {
    ArgumentCaptor<HitlItem> captor = ArgumentCaptor.forClass(HitlItem.class);
    verify(hitlRepo).save(captor.capture());
    return captor.getValue();
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
