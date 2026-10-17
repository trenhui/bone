package com.bone.aiserver.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
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
 * 真实智能理赔场景：数据集驱动的全能力验证。
 *
 * <p>以贴近真实商业医疗险合同的条款、核赔规则与理赔单为测试数据，数据驱动验证全部 AI 能力（document / rules / gateway / trace /
 * orchestration / HITL），并覆盖 HITL 四种人工裁决（confirm / reject / modify / escalate）与置信度三档分档。
 *
 * <p>保单 PDF 用英文条款（PDFBox 标准 Helvetica 字体不支持中文，POC 约束），条款结构对标真实保险合同（责任/免责/等待期/免赔/限额）。
 */
class ClaimRealisticDatasetTest {

  // ===== 真实保单条款（商业医疗险合同，英文，对标真实结构） =====
  private static final String POLICY_CLAUSES =
      "Clause 1: Coverage\n"
          + "This policy covers hospitalization, outpatient, surgery and pharmacy expenses.\n"
          + "Clause 2: Exclusions\n"
          + "Pre-existing conditions, cosmetic surgery, rehabilitation and amounts above 50000 are excluded.\n"
          + "Clause 3: Waiting Period\n"
          + "No benefit for claims within 30 days of policy inception except accident.\n"
          + "Clause 4: Deductible\n"
          + "Annual deductible of 3000 applies before reimbursement.\n"
          + "Clause 5: Benefit Limit\n"
          + "Maximum annual benefit is 200000.\n";

  // ===== 核赔规则集（QLExpress 对 amount，贴近真实核赔分档） =====
  private RuleAsset r01_exclusionLargeAmount;
  private RuleAsset r02_riskReview;
  private RuleAsset r03_fastTrackSmall;

  private RuleAssetRepository ruleRepo;
  private RuleAssetVersionRepository ruleVersionRepo;
  private EvidenceSnapshotRepository evidenceRepo;
  private HitlItemRepository hitlRepo;
  private HitlDispositionRepository dispositionRepo;
  private final List<HitlDisposition> dispositions = new ArrayList<>();
  private final List<DomainEvent> published = new ArrayList<>();
  private RuleApplicationService ruleService;
  private RuleQueryApplicationService ruleQueryService;
  private ClaimService claimService;

  @BeforeEach
  void setUp() throws IOException {
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

    // 规则集：R01 大额免责短路 / R02 风控命中不短路 / R03 小额速赔
    r01_exclusionLargeAmount = rule("R01", "EXCLUDE", "大额免责", "amount >= 50000", true);
    r02_riskReview = rule("R02", "RISK", "大额风控复核", "amount >= 30000", false);
    r03_fastTrackSmall = rule("R03", "FAST", "小额速赔", "amount <= 2000", false);
    when(ruleRepo.findActive("1"))
        .thenReturn(List.of(r01_exclusionLargeAmount, r02_riskReview, r03_fastTrackSmall));

    final EvidenceTraceListener listener =
        new EvidenceTraceListener(evidenceRepo, (TenantProvider) () -> 42L);
    EvidenceQueryApplicationService evidenceQuery =
        new EvidenceQueryApplicationService(evidenceRepo);

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

    LlmGatewayApplicationService gateway =
        new LlmGatewayApplicationService(
            new StubLlmClient(0.92),
            bus,
            (com.bone.ai.gateway.domain.gateway.TenantProvider) () -> "42");
    OrchestrationApplicationService orchestration =
        new OrchestrationApplicationService(ruleService, ruleQueryService, gateway);
    HitlApplicationService hitlService = new HitlApplicationService(hitlRepo, dispositionRepo);
    claimService = new ClaimService(orchestration, new PdfBoxDocumentParser(), hitlService);
  }

  private static RuleAsset rule(
      String code, String type, String name, String expr, boolean hitBlock) {
    RuleAsset asset = RuleAsset.create("1", type, name, name);
    asset.setId(code.toLowerCase());
    asset.publishFirst(expr, hitBlock, 0, "ops");
    return asset;
  }

  // ===== 1) 大额免责：R01 命中短路，不调 LLM，无事件无留痕 =====
  @Test
  void dataset_large_amount_excluded_short_circuits_and_parses_ir() throws IOException {
    ClaimService.ClaimDecision d =
        underwrite("CLAIM-001", "大额手术医疗费报销，金额远超给付上限，要求全额理赔", 80000.0, "LOW");

    // document：5 条款全切片
    assertThat(d.ir().clauses()).hasSize(5);
    assertThat(d.ir().clauses().get(1).clauseNo()).startsWith("Clause 2");

    // rules + orchestration：R01 免责命中短路
    assertThat(d.mode()).isEqualTo("SHORT_CIRCUITED");
    assertThat(d.verdict()).isEqualTo("AUTO_REJECT");
    assertThat(d.orchestration().blockedRuleCode()).isEqualTo("EXCLUDE"); // blockedRuleCode 为规则类型

    // gateway 成本红线：短路不调 LLM ⇒ 无计量、无证据、无留痕
    assertThat(published).isEmpty();
    verify(evidenceRepo, never()).insert(any());
  }

  // ===== 2) 常规小额：R03 命中不短路 → LLM 评估（0.92 ≥ 0.85）→ 自动通过 + 证据链回放 + 计量 =====
  @Test
  void dataset_normal_claim_llm_evaluated_auto_approve_trace_replay_and_usage() throws IOException {
    ClaimService.ClaimDecision d = underwrite("CLAIM-002", "门诊感冒发热就诊，处方药费报销", 2000.0, "LOW");

    assertThat(d.mode()).isEqualTo("LLM_EVALUATED");
    assertThat(d.verdict()).isEqualTo("AUTO_APPROVE");
    assertThat(d.confidence()).isEqualTo(0.92);
    assertThat(d.orchestration().llmResponse()).isNotNull();

    // gateway：发布计量 + 证据事件（双轨）
    LlmUsageRecorded usage =
        published.stream()
            .filter(e -> e instanceof LlmUsageRecorded)
            .map(e -> (LlmUsageRecorded) e)
            .findFirst()
            .orElseThrow();
    assertThat(usage.model()).isEqualTo("claim-assessor");
    assertThat(usage.usage().totalTokens()).isEqualTo(15); // StubLlmClient 固定 10+5

    // trace：真实 traceId 可回放证据链（LLM_CALL 快照）
    LlmCallRecorded call =
        published.stream()
            .filter(e -> e instanceof LlmCallRecorded)
            .map(e -> (LlmCallRecorded) e)
            .findFirst()
            .orElseThrow();
    ArgumentCaptor<EvidenceSnapshot> captor = ArgumentCaptor.forClass(EvidenceSnapshot.class);
    verify(evidenceRepo).insert(captor.capture());
    EvidenceSnapshot saved = captor.getValue();
    assertThat(saved.type()).isEqualTo(EvidenceType.LLM_CALL);
    assertThat(saved.getTraceId()).isEqualTo(call.traceId());
    assertThat(saved.getTenantId()).isEqualTo(42L);
    assertThat(saved.getEngineVersion()).isNotBlank();
  }

  // ===== 3) 高风险 HIGH：即使置信 0.92 也强制人工终审（风险等级优先于置信度） =====
  @Test
  void dataset_high_risk_forced_to_hitl_regardless_of_confidence() throws IOException {
    ClaimService.ClaimDecision d = underwrite("CLAIM-003", "复杂手术住院治疗，医疗费用较高", 35000.0, "HIGH");

    assertThat(d.mode()).isEqualTo("HITL_PENDING");
    assertThat(d.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(d.hitlStatus()).isEqualTo("PENDING");
    assertThat(d.hitlItemId()).isNotBlank();
  }

  // ===== 4) 低置信 0.5（< 0.7）：强制人工终审 =====
  @Test
  void dataset_low_confidence_forced_to_hitl() throws IOException {
    ClaimService.ClaimDecision d =
        underwrite("CLAIM-004", "手术报销，模型对承保范围把握不足", 30000.0, "MEDIUM", 0.5);

    assertThat(d.mode()).isEqualTo("HITL_PENDING");
    assertThat(d.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(d.confidence()).isEqualTo(0.5);
    assertThat(d.hitlItemId()).isNotBlank();
  }

  // ===== 5) 中等置信 0.78（0.7-0.85 复核档）：进人工复核 =====
  @Test
  void dataset_medium_confidence_goes_to_review_band() throws IOException {
    ClaimService.ClaimDecision d =
        underwrite("CLAIM-005", "住院医疗费报销，待复核是否触发免赔额", 28000.0, "MEDIUM", 0.78);

    assertThat(d.mode()).isEqualTo("HITL_PENDING");
    assertThat(d.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(d.confidence()).isEqualTo(0.78);
  }

  // ===== 6) HITL 四种人工裁决全验证（confirm / reject / modify / escalate + 处置链回放） =====
  @Test
  void dataset_hitl_all_dispositions_flow_and_replay() throws IOException {
    // confirm：模型建议核赔，人工确认放行 → CONFIRMED 终态
    HitlItem confirmItem = pendingItem("CLAIM-006", "手术报销", 30000.0);
    HitlItem.Disposition confirm =
        claimService.act(
            "1", confirmItem.getId(), "ops-1", "confirm", null, ReasonType.CONTEXT_MISS, "核赔一致");
    assertThat(confirm.action()).isEqualTo("confirm");
    assertThat(confirmItem.getStatus()).isEqualTo(HitlStatus.CONFIRMED);

    // reject：既往症免责，驳回并回流 RULE_MISS → REJECTED 终态
    HitlItem rejectItem = pendingItem("CLAIM-007", "手术报销", 30000.0);
    HitlItem.Disposition reject =
        claimService.act(
            "1", rejectItem.getId(), "ops-2", "reject", null, ReasonType.RULE_MISS, "既往症漏判");
    assertThat(reject.action()).isEqualTo("reject");
    assertThat(reject.reasonType()).isEqualTo("RULE_MISS");
    assertThat(rejectItem.getStatus()).isEqualTo(HitlStatus.REJECTED);

    // modify：人工修正核赔金额，携带纠正值与原因
    HitlItem modifyItem = pendingItem("CLAIM-008", "手术报销", 30000.0);
    HitlItem.Disposition modify =
        claimService.act(
            "1", modifyItem.getId(), "ops-3", "modify", 26000.0, ReasonType.CONTEXT_MISS, "免赔额调整");
    assertThat(modify.action()).isEqualTo("modify");
    assertThat(modify.reasonType()).isEqualTo("CONTEXT_MISS");
    assertThat(modifyItem.getStatus()).isEqualTo(HitlStatus.MODIFIED);

    // escalate：人工无法判断，升级上级复核
    HitlItem escalateItem = pendingItem("CLAIM-009", "手术报销", 30000.0);
    HitlItem.Disposition escalate =
        claimService.act("1", escalateItem.getId(), "ops-4", "escalate", null, null, "证据不足，升级复核");
    assertThat(escalate.action()).isEqualTo("escalate");
    assertThat(escalateItem.getStatus()).isEqualTo(HitlStatus.ESCALATED);

    // 处置链回放（以 reject 项为例）：谁在何时做了什么裁决、带什么原因
    var history = claimService.dispositionHistory("1", rejectItem.getId());
    assertThat(history).hasSize(1);
    assertThat(history.get(0).actor()).isEqualTo("ops-2");
    assertThat(history.get(0).reasonType()).isEqualTo("RULE_MISS");
    assertThat(history.get(0).comment()).isEqualTo("既往症漏判");
  }

  /** 建一个新 PENDING item（underwrite 触发 save，捕获回最新产出项并接回 mock 仓储）。 */
  private HitlItem pendingItem(String claimId, String desc, double amount) throws IOException {
    ClaimService.ClaimDecision d = underwrite(claimId, desc, amount, "MEDIUM", 0.5);
    String itemId = d.hitlItemId();
    ArgumentCaptor<HitlItem> captor = ArgumentCaptor.forClass(HitlItem.class);
    verify(hitlRepo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    // 捕获累积的 save 中最后一项（当次 underwrite 新产出）
    HitlItem item = captor.getAllValues().get(captor.getAllValues().size() - 1);
    when(hitlRepo.findPendingById("1", itemId)).thenReturn(java.util.Optional.of(item));
    when(hitlRepo.findById(itemId)).thenReturn(item);
    return item;
  }

  // ===== 工具 =====
  private ClaimService.ClaimDecision underwrite(
      String claimId, String desc, double amount, String risk) throws IOException {
    return underwrite(claimId, desc, amount, risk, 0.92);
  }

  private ClaimService.ClaimDecision underwrite(
      String claimId, String desc, double amount, String risk, double confidence)
      throws IOException {
    byte[] pdf = policyPdf(POLICY_CLAUSES);
    ClaimService local =
        Math.abs(confidence - 0.92) < 1e-9 ? claimService : buildWithConfidence(confidence);
    return local.underwrite(
        new ClaimService.ClaimRequest(
            "1",
            claimId,
            desc,
            amount,
            risk,
            new ByteArrayInputStream(pdf),
            "policy-" + claimId + ".pdf"));
  }

  private ClaimService buildWithConfidence(double confidence) {
    DomainEventPublisher bus =
        new DomainEventPublisher() {
          @Override
          public void publish(DomainEvent event) {
            published.add(event);
          }

          @Override
          public void publishAll(Collection<? extends DomainEvent> events) {
            events.forEach(this::publish);
          }
        };
    LlmGatewayApplicationService gateway =
        new LlmGatewayApplicationService(
            new StubLlmClient(confidence),
            bus,
            (com.bone.ai.gateway.domain.gateway.TenantProvider) () -> "42");
    OrchestrationApplicationService orch =
        new OrchestrationApplicationService(ruleService, ruleQueryService, gateway);
    HitlApplicationService hitlService = new HitlApplicationService(hitlRepo, dispositionRepo);
    return new ClaimService(orch, new PdfBoxDocumentParser(), hitlService);
  }

  /** 确定性 LLM：content 携带 conf:x，usage 固定（供计量精确断言）。 */
  private static class StubLlmClient implements LlmClient {
    private final double confidence;

    StubLlmClient(double confidence) {
      this.confidence = confidence;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
      return new LlmResponse(
          "conf:" + confidence + " 建议核赔", "claim-assessor", "stop", new LlmUsage(10, 5, 15));
    }
  }

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
