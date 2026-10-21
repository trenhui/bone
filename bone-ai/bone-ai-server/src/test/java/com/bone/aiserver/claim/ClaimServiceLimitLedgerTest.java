package com.bone.aiserver.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.ailedger.domain.model.LimitScope;
import com.bone.ailedger.infrastructure.ledger.InMemoryLimitLedger;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.ReasonType;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 理赔演示接入确定性额度内核（产品域复用 bone-ai-ledger 的验证）。
 *
 * <p>重点验证两阶段生命周期的正确性：自动通过即实扣；超限绝不自动放行（降级转人工 + 编码原因 {@code LIMIT_EXCEEDED}）； 转人工则占位待裁，人工 confirm 提交
 * / reject 释放 / modify 按改后值重占 / escalate 保持占住。
 */
class ClaimServiceLimitLedgerTest {

  private static final LimitScope SCOPE = LimitScope.of("CLAIM_SINGLE", "1");

  private OrchestrationApplicationService orchestration;
  private DocumentParser parser;
  private HitlItemRepository hitlRepo;
  private HitlApplicationService hitlService;
  private InMemoryLimitLedger ledger;
  private ClaimService service;

  @BeforeEach
  void setUp() {
    orchestration = mock(OrchestrationApplicationService.class);
    parser = mock(DocumentParser.class);
    when(parser.parse(any(), any()))
        .thenReturn(ParsedDocument.of(1, List.of("Clause 1"), "pdfbox"));

    hitlRepo = mock(HitlItemRepository.class);
    HitlDispositionRepository dispositionRepo = mock(HitlDispositionRepository.class);
    when(dispositionRepo.insert(any())).thenReturn(1L);
    hitlService = new HitlApplicationService(hitlRepo, dispositionRepo);

    ledger = new InMemoryLimitLedger();
    service = new ClaimService(orchestration, parser, hitlService, ledger);
  }

  private void stubConfidence(double confidence) {
    LlmResponse llm =
        new LlmResponse(
            "{\"confidence\":" + confidence + "}", "claim-assessor", "stop", LlmUsage.empty());
    when(orchestration.run(any())).thenReturn(new OrchestrationResult(List.of(), false, null, llm));
  }

  private ClaimService.ClaimDecision underwrite(String claimId, double amount, String riskLevel) {
    return service.underwrite(
        new ClaimService.ClaimRequest(
            "1",
            claimId,
            "门诊报销",
            amount,
            riskLevel,
            new ByteArrayInputStream(new byte[0]),
            "policy.pdf"));
  }

  /** 取回 underwrite 落库的 HITL 项，并让后续 act() 能按 ID 找到它。 */
  private HitlItem capturePendingItem(String itemId) {
    ArgumentCaptor<HitlItem> captor = ArgumentCaptor.forClass(HitlItem.class);
    org.mockito.Mockito.verify(hitlRepo).save(captor.capture());
    HitlItem created = captor.getValue();
    when(hitlRepo.findPendingById("1", itemId)).thenReturn(Optional.of(created));
    return created;
  }

  private BigDecimal consumed() {
    return ledger.snapshot(SCOPE).consumed();
  }

  private BigDecimal reserved() {
    return ledger.snapshot(SCOPE).reserved();
  }

  @Test
  void auto_pass_within_limit_commits_immediately() {
    stubConfidence(0.92);

    ClaimService.ClaimDecision d = underwrite("C1", 2000.0, "LOW");

    assertThat(d.mode()).isEqualTo("LLM_EVALUATED");
    assertThat(d.verdict()).isEqualTo("AUTO_APPROVE");
    assertThat(d.reasonCode()).isEqualTo("AUTO_APPROVED");
    // 自动通过 ⇒ 占位即实扣（无需人工再收尾）
    assertThat(consumed()).isEqualByComparingTo("2000");
    assertThat(reserved()).isEqualByComparingTo("0");
  }

  @Test
  void auto_pass_exceeding_limit_demotes_to_hitl_with_coded_reason() {
    stubConfidence(0.92); // 置信度足够高，本应自动通过

    ClaimService.ClaimDecision d = underwrite("C2", 60000.0, "LOW");

    // 超限绝不自动放行：降级转人工，且原因码是确定性超限（而非置信度分档）
    assertThat(d.mode()).isEqualTo("HITL_PENDING");
    assertThat(d.verdict()).isEqualTo("UNDER_REVIEW");
    assertThat(d.reasonCode()).isEqualTo("LIMIT_EXCEEDED");
    // 占位失败 ⇒ 未占用任何额度
    assertThat(consumed()).isEqualByComparingTo("0");
    assertThat(reserved()).isEqualByComparingTo("0");
  }

  @Test
  void hitl_reserve_then_confirm_commits() {
    stubConfidence(0.5); // 低置信 ⇒ 转人工

    ClaimService.ClaimDecision d = underwrite("C3", 30000.0, "LOW");
    assertThat(d.mode()).isEqualTo("HITL_PENDING");
    assertThat(d.reasonCode()).isEqualTo("CONFIDENCE_LOW");
    assertThat(reserved()).isEqualByComparingTo("30000"); // 占位待裁，防并发双花

    capturePendingItem(d.hitlItemId());
    service.act("1", d.hitlItemId(), "ops-1", "confirm", null, ReasonType.RULE_MISS, null);

    assertThat(consumed()).isEqualByComparingTo("30000");
    assertThat(reserved()).isEqualByComparingTo("0");
  }

  @Test
  void hitl_reserve_then_reject_releases() {
    stubConfidence(0.5);

    ClaimService.ClaimDecision d = underwrite("C4", 30000.0, "LOW");
    capturePendingItem(d.hitlItemId());

    service.act("1", d.hitlItemId(), "ops-1", "reject", null, ReasonType.RULE_MISS, "既往症");

    // 不赔 ⇒ 额度全额返还
    assertThat(consumed()).isEqualByComparingTo("0");
    assertThat(reserved()).isEqualByComparingTo("0");
    assertThat(ledger.available(SCOPE)).isEqualByComparingTo("50000");
  }

  @Test
  void hitl_reserve_then_modify_re_reserves_corrected_value() {
    stubConfidence(0.5);

    ClaimService.ClaimDecision d = underwrite("C5", 30000.0, "LOW");
    capturePendingItem(d.hitlItemId());

    service.act(
        "1", d.hitlItemId(), "ops-1", "modify", 10000.0, ReasonType.SUGGESTION_WRONG, "金额偏大");

    // 释放原占位，按改后值重新占位并实扣
    assertThat(consumed()).isEqualByComparingTo("10000");
    assertThat(reserved()).isEqualByComparingTo("0");
  }

  @Test
  void escalate_keeps_reservation_held() {
    stubConfidence(0.5);

    ClaimService.ClaimDecision d = underwrite("C6", 30000.0, "LOW");
    capturePendingItem(d.hitlItemId());

    service.act("1", d.hitlItemId(), "ops-1", "escalate", null, null, "升级全程处理");

    // 仍在审 ⇒ 额度继续占住，不被后续案件抢占用
    assertThat(reserved()).isEqualByComparingTo("30000");
    assertThat(consumed()).isEqualByComparingTo("0");
  }

  @Test
  void without_ledger_behaviour_is_unchanged() {
    // 未接入额度内核（3 参构造）：行为与接入前完全一致，不因额度逻辑抛错
    ClaimService noLedger = new ClaimService(orchestration, parser, hitlService);
    stubConfidence(0.92);

    ClaimService.ClaimDecision d =
        noLedger.underwrite(
            new ClaimService.ClaimRequest(
                "1",
                "C7",
                "门诊报销",
                999999.0,
                "LOW",
                new ByteArrayInputStream(new byte[0]),
                "policy.pdf"));

    assertThat(d.mode()).isEqualTo("LLM_EVALUATED");
    assertThat(d.verdict()).isEqualTo("AUTO_APPROVE");
  }
}
