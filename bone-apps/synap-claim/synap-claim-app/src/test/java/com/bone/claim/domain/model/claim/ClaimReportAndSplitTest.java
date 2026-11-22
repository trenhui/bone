package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 1:N 领域模型单测：报案聚合不变量 + 分案复制边界（不克隆理算/结算结果）。 */
class ClaimReportAndSplitTest {

  private static final long TENANT = 1L;

  private ClaimReport report(long id) {
    return ClaimReport.register(
        id,
        TENANT,
        "idem-report-" + id,
        "PLICY-001",
        "hash-abc",
        Instant.parse("2026-11-20T08:00:00Z"),
        "就诊骨折",
        "APP",
        "self");
  }

  private ClaimCase mother(Long reportId, String reportNo) {
    ClaimCase m =
        ClaimCase.draft(
            1000L,
            TENANT,
            "PLICY-001",
            9L,
            "hash-abc",
            "OUTPATIENT",
            "FP",
            "INV-1",
            Instant.parse("2026-11-20T08:00:00Z"),
            "",
            "idem-mother",
            "v1",
            List.of(
                ClaimItem.of(
                    1L,
                    TENANT,
                    1000L,
                    "挂号费",
                    "",
                    null,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    BigDecimal.TEN)));
    m.assignReport(reportId, reportNo);
    // 模拟母案已结案并回填核定金额——分案绝不能克隆这些
    m.applyApprovedAmount(BigDecimal.TEN);
    return m;
  }

  @Test
  @DisplayName("报案登记：reportNo 由 id 派生(RP前缀)，可逆推报案主键，初始 REPORTED/caseCount=0")
  void registerInvariants() {
    long id = 76102367333449728L;
    ClaimReport report = report(id);

    assertThat(report.getReportNo()).startsWith("RP");
    assertThat(ReportNoGenerator.isValid(report.getReportNo())).isTrue();
    assertThat(ReportNoGenerator.extractSnowflakeId(report.getReportNo())).isEqualTo(id);
    assertThat(ClaimNoGenerator.isValid(report.getReportNo())).isFalse();
    assertThat(report.getStatus()).isEqualTo(ClaimReport.STATUS_REPORTED);
    assertThat(report.getCaseCount()).isZero();
    assertThat(report.canSplit()).isTrue();
  }

  @Test
  @DisplayName("报案关闭：close 幂等，关闭后不可再分案；onCaseCreated 在关闭态拒绝")
  void closeAndSplitGuard() {
    ClaimReport report = report(200L);
    report.onCaseCreated();
    assertThat(report.getCaseCount()).isEqualTo(1);

    assertThat(report.close()).isTrue();
    assertThat(report.close()).isFalse();
    assertThat(report.canSplit()).isFalse();
    assertThatThrownBy(report::onCaseCreated).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("登记入参校验：保单/被保人/出险时间/幂等键缺失均拒绝")
  void rejectsMissing() {
    assertThatThrownBy(
            () -> ClaimReport.register(1L, TENANT, "idem", "", "h", Instant.now(), "", "", ""))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> ClaimReport.register(1L, TENANT, "idem", "p", "", Instant.now(), "", "", ""))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> ClaimReport.register(1L, TENANT, "idem", "p", "h", null, "", "", ""))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> ClaimReport.register(1L, TENANT, "", "p", "h", Instant.now(), "", "", ""))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("分案复制：子案继承保单/被保人/报案归属，但生成新赔案号、新幂等键、不克隆核定金额")
  void splitCopiesContextNotResults() {
    ClaimReport report = report(300L);
    ClaimCase mother = mother(report.getId(), report.getReportNo());

    List<ClaimItem> childItems =
        List.of(
            ClaimItem.of(
                2L,
                TENANT,
                9999L,
                "手术费",
                "",
                null,
                BigDecimal.ONE,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100)));
    ClaimCase child =
        ClaimCase.splitFrom(
            mother,
            9999L,
            "idem-child",
            "FP",
            "INV-2",
            LocalDate.of(2026, 11, 21),
            "H001",
            "D001",
            childItems);

    assertThat(child.getId()).isEqualTo(9999L);
    assertThat(child.getClaimNo()).startsWith("CL");
    assertThat(child.getClaimNo()).isNotEqualTo(mother.getClaimNo());
    assertThat(ClaimNoGenerator.isValid(child.getClaimNo())).isTrue();
    assertThat(child.getReportId()).isEqualTo(report.getId());
    assertThat(child.getReportNo()).isEqualTo(report.getReportNo());
    assertThat(child.getPolicyNo()).isEqualTo(mother.getPolicyNo());
    assertThat(child.getInsuredIdHash()).isEqualTo(mother.getInsuredIdHash());
    assertThat(child.getIdempotencyKey()).isEqualTo("idem-child");
    assertThat(child.getApprovedAmount()).isNull();
    assertThat(child.getClaimAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    assertThat(child.externalStatus()).isEqualTo("PENDING_EVIDENCE");
  }

  @Test
  @DisplayName("分案守卫：母案未绑定报案 / 幂等键空 / 明细空 均拒绝")
  void splitGuards() {
    ClaimCase unbound =
        ClaimCase.draft(
            1001L,
            TENANT,
            "p",
            9L,
            "h",
            "OUTPATIENT",
            "FP",
            "INV-1",
            Instant.now(),
            "",
            "idem",
            "v1",
            List.of(
                ClaimItem.of(
                    3L,
                    TENANT,
                    1001L,
                    "x",
                    "",
                    null,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE)));
    assertThatThrownBy(
            () -> ClaimCase.splitFrom(unbound, 2L, "k", null, null, null, "", "", List.of()))
        .isInstanceOf(DomainException.class);

    ClaimReport report = report(400L);
    ClaimCase mother = mother(report.getId(), report.getReportNo());
    assertThatThrownBy(
            () -> ClaimCase.splitFrom(mother, 2L, "", null, null, null, "", "", List.of()))
        .isInstanceOf(DomainException.class);
  }
}
