package com.bone.aiserver.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aidoc.domain.model.DocumentIR;
import com.bone.aiorch.domain.hitl.ReasonType;
import com.bone.aiserver.claim.ClaimService;
import com.bone.aiserver.claim.ClaimService.ClaimDecision;
import com.bone.aiserver.claim.ClaimService.ClaimRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 理赔演示端点委托单测（纯 JUnit，不 boot——与 AiPipelineControllerTest 同策略）。 覆盖控制器把理赔请求转交 ClaimService 的接线 + 人工终审
 * act 的 ReasonType 绑定。
 */
class ClaimControllerTest {

  private final ClaimService claimService = mock(ClaimService.class);
  private final ClaimController controller = new ClaimController(claimService);

  @Test
  void underwrite_delegatesToClaimService() {
    ClaimDecision decision =
        new ClaimDecision(
            "claim-1",
            "LLM_EVALUATED",
            "AUTO_APPROVE",
            DocumentIR.of("claim-1", "t1", "p.pdf", 1, "PDFBOX_TEXT", "pdfbox", List.of()),
            null,
            null,
            0.9,
            null);
    when(claimService.underwrite(org.mockito.ArgumentMatchers.any(ClaimRequest.class)))
        .thenReturn(decision);
    MockMultipartFile pdf =
        new MockMultipartFile("policyPdf", "policy.pdf", "application/pdf", new byte[0]);

    ClaimDecision got = controller.underwrite("t1", "claim-1", "门诊报销", 2000.0, "LOW", pdf);

    assertThat(got.claimId()).isEqualTo("claim-1");
    assertThat(got.verdict()).isEqualTo("AUTO_APPROVE");
    verify(claimService).underwrite(org.mockito.ArgumentMatchers.any(ClaimRequest.class));
  }

  @Test
  void act_binds_reason_type() {
    when(claimService.act("t1", "hitl-1", "ops-1", "reject", null, ReasonType.RULE_MISS, "既往症漏判"))
        .thenReturn(null);
    Object result = controller.act("t1", "hitl-1", "ops-1", "reject", null, "RULE_MISS", "既往症漏判");
    assertThat(result).isNull();
    verify(claimService)
        .act("t1", "hitl-1", "ops-1", "reject", null, ReasonType.RULE_MISS, "既往症漏判");
  }

  @Test
  void history_delegates_to_claimService() {
    when(claimService.dispositionHistory("t1", "hitl-1")).thenReturn(List.of());
    assertThat(controller.history("t1", "hitl-1")).isEmpty();
    verify(claimService).dispositionHistory("t1", "hitl-1");
  }
}
