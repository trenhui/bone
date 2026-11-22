package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.ClaimReportApplicationService.AppendCaseCommand;
import com.bone.claim.application.ClaimReportApplicationService.ReportDetail;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import com.bone.core.exception.BizException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 报案视角应用服务单测：继承而非覆盖、CLOSED 拒绝、404 防枚举、详情派生口径。
 *
 * <p>这层的核心风险不是「算错」，而是「悄悄放开」——追加赔案若从请求里读保单号/出险时间， 结构上就允许把 A 保单的赔付挂到 B 事故上。所以本类重点钉「提交给建案管线的命令里，
 * 事故维度只可能来自报案」。
 */
@ExtendWith(MockitoExtension.class)
class ClaimReportApplicationServiceTest {

  private static final long TENANT = 42L;
  private static final long REPORT_ID = 76102367333449728L;
  private static final String REPORT_NO = "RP20261122ABCDEFGH1234";
  private static final Instant OUT_INSURE = Instant.parse("2026-03-12T10:00:00Z");

  @Mock private ClaimReportRepository claimReportRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private TenantPort tenantPort;
  @InjectMocks private ClaimReportApplicationService service;

  private ClaimReport report;

  @BeforeEach
  void setUp() {
    report =
        ClaimReport.open(
            REPORT_ID, TENANT, "EG20260001", 7L, "hash-abc", "OUTPATIENT", OUT_INSURE, "v3", "");
    // lenient：不是每个用例都走 requireReport（如 allCasesFinished / 常量断言），
    // 严格模式会把这些共用的默认桩判成 UnnecessaryStubbing。
    org.mockito.Mockito.lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT);
    org.mockito.Mockito.lenient()
        .when(claimReportRepository.findByReportNo(TENANT, REPORT_NO))
        .thenReturn(report);
  }

  private AppendCaseCommand command(String idempotencyKey) {
    return new AppendCaseCommand(
        REPORT_NO,
        idempotencyKey,
        "OUTPATIENT",
        "04400001",
        "12345678",
        null,
        "",
        "",
        "",
        null,
        "",
        List.of());
  }

  @Nested
  @DisplayName("追加赔案")
  class AppendCase {

    @Test
    @DisplayName("事故上下文全部从报案继承：命令里保单号为空，计划/被保人/出险时间取报案值")
    void inheritsIncidentContextFromReport() {
      when(claimCaseApplicationService.appendCase(any(), any()))
          .thenReturn(new SubmittedClaim(1002L, "CL20261122NEWCASE", REPORT_NO));

      service.appendCase(command("idem-append-1"));

      ArgumentCaptor<SubmitClaim> captor = ArgumentCaptor.forClass(SubmitClaim.class);
      verify(claimCaseApplicationService).appendCase(eq(report), captor.capture());

      SubmitClaim submitted = captor.getValue();
      // 两个保单标识都为空：追加不重新解析保单，值由 buildClaim 从报案取
      assertThat(submitted.policyNo()).isNull();
      assertThat(submitted.insurerPolicyNo()).isNull();
      // 事故维度三项必须等于报案上的值（不是请求里的值——请求里根本没有这些字段）
      assertThat(submitted.planId()).isEqualTo(report.getPlanId());
      assertThat(submitted.insuredIdHash()).isEqualTo(report.getInsuredIdHash());
      assertThat(submitted.outInsureTime()).isEqualTo(report.getOutInsureTime());
      // 幂等键原样透传（追加的去重责任在赔案侧）
      assertThat(submitted.idempotencyKey()).isEqualTo("idem-append-1");
    }

    @Test
    @DisplayName("报案已关闭 → 422 SC_CLAIM_REPORT_CLOSED，且不进入建案管线")
    void rejectsClosedReport() {
      report.close();

      assertThatThrownBy(() -> service.appendCase(command("idem-append-2")))
          .isInstanceOf(BizException.class)
          // 报案号由聚合从雪花 id 派生，测试无法指定，故断言用报文上的真实号
          // （文案带号是关键：用户拿到的 422 必须能定位到是哪条报案被关闭了）
          .hasMessageContaining(report.getReportNo());

      verify(claimCaseApplicationService, never()).appendCase(any(), any());
    }

    @Test
    @DisplayName("报案不存在（或跨租户同号）→ 404 SC_CLAIM_REPORT_NOT_FOUND，不区分两者")
    void rejectsMissingReport() {
      when(claimReportRepository.findByReportNo(TENANT, "RP-NOPE")).thenReturn(null);

      AppendCaseCommand cmd =
          new AppendCaseCommand(
              "RP-NOPE",
              "idem-append-3",
              "OUTPATIENT",
              null,
              null,
              null,
              "",
              "",
              "",
              null,
              "",
              List.of());

      assertThatThrownBy(() -> service.appendCase(cmd)).isInstanceOf(BizException.class);

      verify(claimCaseApplicationService, never()).appendCase(any(), any());
    }
  }

  @Nested
  @DisplayName("报案详情")
  class Detail {

    @Test
    @DisplayName("按报文的租户与 id 查同案赔案，返回报案头 + 清单")
    void returnsReportWithCases() {
      ClaimCase first = mock(ClaimCase.class);
      ClaimCase second = mock(ClaimCase.class);
      when(claimCaseRepository.findByReportId(TENANT, REPORT_ID))
          .thenReturn(List.of(first, second));

      ReportDetail detail = service.detail(REPORT_NO);

      assertThat(detail.report()).isSameAs(report);
      assertThat(detail.cases()).hasSize(2);
    }

    @Test
    @DisplayName("报案不存在 → 抛错（不返回空清单：空清单与「无此报案」是两件事）")
    void missingReportThrows() {
      when(claimReportRepository.findByReportNo(TENANT, "RP-NOPE")).thenReturn(null);

      assertThatThrownBy(() -> service.detail("RP-NOPE")).isInstanceOf(BizException.class);
      verify(claimCaseRepository, never()).findByReportId(any(), any());
    }

    @Test
    @DisplayName("allCasesFinished 是派生量：全部结案才为 true，空清单为 false")
    void allCasesFinishedIsDerived() {
      ClaimCase finished = mock(ClaimCase.class);
      when(finished.finished()).thenReturn(true);
      ClaimCase inFlight = mock(ClaimCase.class);
      when(inFlight.finished()).thenReturn(false);

      assertThat(new ReportDetail(report, List.of()).allCasesFinished())
          .as("0 笔赔案不等于「全部结案」")
          .isFalse();
      assertThat(new ReportDetail(report, List.of(finished)).allCasesFinished()).isTrue();
      assertThat(new ReportDetail(report, List.of(finished, inFlight)).allCasesFinished())
          .isFalse();
    }

    @Test
    @DisplayName("错误码常量与登记表一致（防改了常量忘了台账）")
    void errorCodesAreTheRegisteredOnes() {
      assertThat(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND).isEqualTo("SC_CLAIM_REPORT_NOT_FOUND");
      assertThat(ClaimErrorCodes.CLAIM_REPORT_CLOSED).isEqualTo("SC_CLAIM_REPORT_CLOSED");
      assertThat(ClaimErrorCodes.CLAIM_REPORT_CONTEXT_MISMATCH)
          .isEqualTo("SC_CLAIM_REPORT_CONTEXT_MISMATCH");
    }
  }
}
