package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimReportApplicationService.RegisterReport;
import com.bone.claim.application.ClaimReportApplicationService.RegisteredReport;
import com.bone.claim.application.ClaimReportApplicationService.SplitCase;
import com.bone.claim.application.ClaimReportApplicationService.SplitCase.ClaimItemDto;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 1:N 报案应用单测：登记幂等 + 分案（继承上下文/新号/不克隆结果）+ 关闭守卫。 */
@ExtendWith(MockitoExtension.class)
class ClaimReportApplicationServiceTest {

  private static final long TENANT = 42L;

  @Mock private ClaimReportRepository claimReportRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private TenantPort tenantPort;
  @InjectMocks private ClaimReportApplicationService service;

  private RegisterReport registerCommand() {
    return new RegisterReport(
        "idem-r1", "POL-1", "hash-a", Instant.parse("2026-11-20T08:00:00Z"), "骨折", "APP", "self");
  }

  private ClaimReport existingReport() {
    return ClaimReport.register(
        100L,
        TENANT,
        "idem-r1",
        "POL-1",
        "hash-a",
        Instant.parse("2026-11-20T08:00:00Z"),
        "骨折",
        "APP",
        "self");
  }

  private ClaimCase mother(ClaimReport report) {
    ClaimCase m =
        ClaimCase.draft(
            200L,
            TENANT,
            "POL-1",
            7L,
            "hash-a",
            "OUTPATIENT",
            "FP",
            "INV-1",
            Instant.parse("2026-11-20T08:00:00Z"),
            "",
            "idem-c1",
            "v1",
            List.of(
                ClaimItem.of(
                    1L,
                    TENANT,
                    200L,
                    "挂号",
                    "",
                    null,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)));
    m.assignReport(report.getId(), report.getReportNo());
    m.accept();
    return m;
  }

  @BeforeEach
  void setUp() {
    lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT);
  }

  @Test
  @DisplayName("登记报案：生成 RP 号并落库")
  void registerPersists() {
    when(claimReportRepository.findByIdempotencyKey(TENANT, "idem-r1")).thenReturn(null);
    RegisteredReport result = service.register(registerCommand());

    assertThat(result.reportNo()).startsWith("RP");
    verify(claimReportRepository).insert(any(ClaimReport.class));
  }

  @Test
  @DisplayName("登记幂等：同幂等键返回首报案，不重复落库")
  void registerIdempotent() {
    ClaimReport first = existingReport();
    when(claimReportRepository.findByIdempotencyKey(TENANT, "idem-r1")).thenReturn(first);
    RegisteredReport result = service.register(registerCommand());

    assertThat(result.reportId()).isEqualTo(first.getId());
    assertThat(result.reportNo()).isEqualTo(first.getReportNo());
  }

  @Test
  @DisplayName("分案：子案继承母案保单/报案归属，生成新 CL 号，不克隆核定金额，不写报案聚合")
  void splitCase() {
    ClaimReport report = existingReport();
    report.onCaseCreated(); // 首案已建
    ClaimCase mother = mother(report);
    when(claimReportRepository.findByReportNo(TENANT, report.getReportNo())).thenReturn(report);
    when(claimCaseRepository.findByReportId(TENANT, report.getId())).thenReturn(List.of(mother));

    int countBefore = report.getCaseCount();
    SplitCase cmd =
        new SplitCase(
            report.getReportNo(),
            "idem-child",
            "FP2",
            "INV-2",
            java.time.LocalDate.of(2026, 11, 21),
            "H1",
            "D1",
            List.of(
                new ClaimItemDto(
                    "手术",
                    "",
                    null,
                    BigDecimal.ONE,
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(100),
                    "",
                    "")));

    var result = service.splitCase(cmd, "idem-child");

    ArgumentCaptor<ClaimCase> captor = ArgumentCaptor.forClass(ClaimCase.class);
    verify(claimCaseRepository).insert(captor.capture());
    ClaimCase child = captor.getValue();
    assertThat(child.getClaimNo()).startsWith("CL");
    assertThat(child.getClaimNo()).isNotEqualTo(mother.getClaimNo());
    assertThat(child.getReportId()).isEqualTo(report.getId());
    assertThat(child.getReportNo()).isEqualTo(report.getReportNo());
    assertThat(child.getApprovedAmount()).isNull();
    assertThat(result.claimNo()).isEqualTo(child.getClaimNo());
    // R9：splitCase 只持久化 ClaimCase 聚合；报案计数由编排层独立事务登记。
    assertThat(report.getCaseCount()).isEqualTo(countBefore);
    verify(claimReportRepository, never()).update(any());
    verify(domainEventPublisher).publishFrom(child);
  }

  @Test
  @DisplayName("报案计数独立事务：recordCaseCreation 递增 caseCount 并落库")
  void recordCaseCreation() {
    ClaimReport report = existingReport();
    int countBefore = report.getCaseCount();
    when(claimReportRepository.findByReportNo(TENANT, report.getReportNo())).thenReturn(report);

    service.recordCaseCreation(report.getReportNo());

    assertThat(report.getCaseCount()).isEqualTo(countBefore + 1);
    verify(claimReportRepository).update(report);
  }

  @Test
  @DisplayName("分案守卫：报案不存在 404 / CLOSED 409")
  void splitGuards() {
    SplitCase cmd =
        new SplitCase(
            "RP-NOT-EXIST",
            "k",
            null,
            null,
            null,
            "",
            "",
            List.of(
                new ClaimItemDto(
                    "x", "", null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "", "")));
    when(claimReportRepository.findByReportNo(TENANT, "RP-NOT-EXIST")).thenReturn(null);
    assertThatThrownBy(() -> service.splitCase(cmd, "k"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND));

    ClaimReport closed = existingReport();
    closed.close();
    when(claimReportRepository.findByReportNo(TENANT, closed.getReportNo())).thenReturn(closed);
    SplitCase cmd2 =
        new SplitCase(closed.getReportNo(), "k2", null, null, null, "", "", cmd.items());
    assertThatThrownBy(() -> service.splitCase(cmd2, "k2"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_REPORT_CLOSED));
  }

  @Test
  @DisplayName("列出报案下赔案：按报案号定位，404 不存在")
  void listCases() {
    when(claimReportRepository.findByReportNo(TENANT, "RP-X")).thenReturn(null);
    assertThatThrownBy(() -> service.listCases("RP-X")).isInstanceOf(BizException.class);
  }
}
