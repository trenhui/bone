package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimReportApplicationService.RegisterReport;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredCase;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredReport;
import com.bone.claim.application.ClaimReportApplicationService.SplitCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 跨聚合编排单测：锁定「报案→建案」顺序、双号贯通与 R9 两段独立事务边界。 */
@ExtendWith(MockitoExtension.class)
class ClaimSubmissionOrchestratorTest {

  @Mock private ClaimReportApplicationService claimReportApplicationService;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @InjectMocks private ClaimSubmissionOrchestrator orchestrator;

  private SubmitClaim command(String policyNo, String insurerPolicyNo) {
    return new SubmitClaim(
        "idem-1",
        policyNo,
        insurerPolicyNo,
        1L,
        "hash-a",
        "MEDICAL",
        "FP",
        "INV-1",
        LocalDate.of(2026, 11, 20),
        "H1",
        "D1",
        Instant.parse("2026-11-20T08:00:00Z"),
        "B1",
        null,
        null,
        List.of());
  }

  @Test
  @DisplayName("提交主链路：先登记报案、再在报案下建首案，双号注入赔案命令")
  void submitRegistersReportThenCreatesCase() {
    SubmitClaim cmd = command("POL-1", null);
    when(claimReportApplicationService.registerFromSubmission(any()))
        .thenReturn(new RegisteredReport(100L, "RP20261120ABCDEFGHIJKLM1"));
    when(claimCaseApplicationService.submit(any(), eq(100L), eq("RP20261120ABCDEFGHIJKLM1")))
        .thenReturn(
            new ClaimCaseApplicationService.SubmittedClaim(
                200L, "CL20261120ABCDEFGHIJKLM1", 100L, "RP20261120ABCDEFGHIJKLM1"));

    var result = orchestrator.submit(cmd);

    InOrder order = inOrder(claimReportApplicationService, claimCaseApplicationService);
    ArgumentCaptor<RegisterReport> reportCmd = ArgumentCaptor.forClass(RegisterReport.class);
    order.verify(claimReportApplicationService).registerFromSubmission(reportCmd.capture());
    order.verify(claimCaseApplicationService).submit(cmd, 100L, "RP20261120ABCDEFGHIJKLM1");
    assertThat(reportCmd.getValue().policyNo()).isEqualTo("POL-1");
    assertThat(reportCmd.getValue().idempotencyKey()).isEqualTo("idem-1");
    assertThat(result.reportId()).isEqualTo(100L);
    assertThat(result.reportNo()).isEqualTo("RP20261120ABCDEFGHIJKLM1");
  }

  @Test
  @DisplayName("只有保司保单号时：报案记录保司号，赔案仍按原命令解析平台号")
  void submitFallsBackToInsurerPolicyNoForReport() {
    SubmitClaim cmd = command(null, "INS-9");
    when(claimReportApplicationService.registerFromSubmission(any()))
        .thenReturn(new RegisteredReport(100L, "RP-1"));
    when(claimCaseApplicationService.submit(any(), eq(100L), eq("RP-1")))
        .thenReturn(new ClaimCaseApplicationService.SubmittedClaim(200L, "CL-1", 100L, "RP-1"));

    orchestrator.submit(cmd);

    ArgumentCaptor<RegisterReport> reportCmd = ArgumentCaptor.forClass(RegisterReport.class);
    verify(claimReportApplicationService).registerFromSubmission(reportCmd.capture());
    assertThat(reportCmd.getValue().policyNo()).isEqualTo("INS-9");
  }

  @Test
  @DisplayName("两个保单号都为空：报案登记前 fail-fast，不产生任何聚合写")
  void submitRejectsMissingPolicyKey() {
    assertThatThrownBy(() -> orchestrator.submit(command(null, null)))
        .isInstanceOf(RuntimeException.class);
    verifyNoInteractions(claimReportApplicationService, claimCaseApplicationService);
  }

  @Test
  @DisplayName("分案：先建子赔案，再以独立调用登记报案计数")
  void splitCaseRecordsCountAfterChildCreation() {
    SplitCase cmd =
        new SplitCase("RP-1", "idem-child", "FP2", "INV-2", null, "H1", "D1", List.of());
    when(claimReportApplicationService.splitCase(cmd, "idem-child"))
        .thenReturn(new RegisteredCase(300L, "CL-child"));

    var result = orchestrator.splitCase(cmd, "idem-child");

    InOrder order = inOrder(claimReportApplicationService);
    order.verify(claimReportApplicationService).splitCase(cmd, "idem-child");
    order.verify(claimReportApplicationService).recordCaseCreation("RP-1");
    assertThat(result.claimId()).isEqualTo(300L);
  }
}
