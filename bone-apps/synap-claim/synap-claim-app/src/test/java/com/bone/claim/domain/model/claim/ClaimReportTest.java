package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.claim.valueobject.ReportStatus;
import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 报案聚合根领域单测：编号派生、必填不变量、状态机（只两值）与幂等关闭。 */
class ClaimReportTest {

  private static final long REPORT_ID = 76102367333449728L;
  private static final Long TENANT = 42L;
  private static final Instant OUT_INSURE = Instant.parse("2026-03-12T10:00:00Z");

  private static ClaimReport open() {
    return ClaimReport.open(
        REPORT_ID, TENANT, "EG20260001", 7L, "hash-abc", "OUTPATIENT", OUT_INSURE, "v3", "");
  }

  @Nested
  @DisplayName("报案号由 id 派生（客户端不可指定）")
  class ReportNumber {

    @Test
    @DisplayName("报案号是 RP 前缀的 24 字符串，且可反解回物理 id")
    void reportNoDerivedFromId() {
      ClaimReport report = open();

      assertThat(report.getReportNo()).startsWith("RP").hasSize(24);
      assertThat(ReportNoGenerator.isValid(report.getReportNo())).isTrue();
      assertThat(ReportNoGenerator.extractSnowflakeId(report.getReportNo()))
          .as("报案号必须能从物理 id 反解——这是「编号不可伪造」的实证")
          .isEqualTo(REPORT_ID);
    }

    @Test
    @DisplayName("报案号与赔案号是两个命名空间：同 id 下互不相等，且互不可解析")
    void reportNoIsNotClaimNo() {
      ClaimReport report = open();
      String claimNo = ClaimNoGenerator.generate(REPORT_ID);

      assertThat(report.getReportNo()).isNotEqualTo(claimNo);
      assertThat(ReportNoGenerator.isValid(claimNo)).isFalse();
      assertThat(ClaimNoGenerator.isValid(report.getReportNo())).isFalse();
    }

    @Test
    @DisplayName("不同 id 产出不同报案号（避免「同一号码指向两个事故」）")
    void distinctIdsDistinctNumbers() {
      ClaimReport a =
          ClaimReport.open(
              REPORT_ID, TENANT, "EG20260001", 7L, "hash-abc", "OUTPATIENT", OUT_INSURE, "v3", "");
      ClaimReport b =
          ClaimReport.open(
              REPORT_ID + 1,
              TENANT,
              "EG20260001",
              7L,
              "hash-abc",
              "OUTPATIENT",
              OUT_INSURE,
              "v3",
              "");

      assertThat(a.getReportNo()).isNotEqualTo(b.getReportNo());
    }
  }

  @Nested
  @DisplayName("必填不变量（事故上下文缺一项，赔案继承就会缺）")
  class Invariants {

    @Test
    @DisplayName("缺保单号 → 拒绝")
    void rejectsBlankPolicyNo() {
      assertThatThrownBy(
              () ->
                  ClaimReport.open(
                      REPORT_ID, TENANT, "  ", 7L, "hash-abc", "OUTPATIENT", OUT_INSURE, "v3", ""))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("保单号");
    }

    @Test
    @DisplayName("缺计划ID → 拒绝")
    void rejectsNullPlanId() {
      assertThatThrownBy(
              () ->
                  ClaimReport.open(
                      REPORT_ID,
                      TENANT,
                      "EG20260001",
                      null,
                      "hash-abc",
                      "OUTPATIENT",
                      OUT_INSURE,
                      "v3",
                      ""))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("计划");
    }

    @Test
    @DisplayName("缺被保人哈希 → 拒绝（禁止明文入参）")
    void rejectsBlankInsuredIdHash() {
      assertThatThrownBy(
              () ->
                  ClaimReport.open(
                      REPORT_ID, TENANT, "EG20260001", 7L, "", "OUTPATIENT", OUT_INSURE, "v3", ""))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("假名化");
    }

    @Test
    @DisplayName("缺出险时间 → 拒绝（事故时间是继承源，不允许缺省）")
    void rejectsNullOutInsureTime() {
      assertThatThrownBy(
              () ->
                  ClaimReport.open(
                      REPORT_ID,
                      TENANT,
                      "EG20260001",
                      7L,
                      "hash-abc",
                      "OUTPATIENT",
                      null,
                      "v3",
                      ""))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("出险时间");
    }

    @Test
    @DisplayName("险种为空 → 按门诊兜底；计划版本为空串（不落 NULL，避免下游判空分支）")
    void defaultsForOptionalFields() {
      ClaimReport report =
          ClaimReport.open(
              REPORT_ID, TENANT, "EG20260001", 7L, "hash-abc", null, OUT_INSURE, null, null);

      assertThat(report.getClaimType()).isEqualTo("OUTPATIENT");
      assertThat(report.getPlanVersion()).isEmpty();
      assertThat(report.getSourceChannel()).isEmpty();
      assertThat(report.getSourceSystem()).isEqualTo("self");
    }

    @Test
    @DisplayName("tenantId / version / 时间戳在创建时齐备（SDK 落库不补默认值）")
    void auditFieldsPresent() {
      ClaimReport report = open();

      assertThat(report.getTenantId()).isEqualTo(TENANT);
      assertThat(report.getVersion()).isZero();
      assertThat(report.getCreatedAt()).isNotNull();
      assertThat(report.getUpdatedAt()).isEqualTo(report.getCreatedAt());
    }
  }

  @Nested
  @DisplayName("状态机：只表达「能否追加」，幂等关闭")
  class StateMachine {

    @Test
    @DisplayName("新报案 = REPORTED，可追加")
    void newReportIsClaimable() {
      ClaimReport report = open();

      assertThat(report.status()).isEqualTo(ReportStatus.REPORTED);
      assertThat(report.claimable()).isTrue();
    }

    @Test
    @DisplayName("关闭后 CLOSED 且不可追加")
    void closedIsNotClaimable() {
      ClaimReport report = open();
      Instant before = report.getUpdatedAt();

      assertThat(report.close()).isTrue();
      assertThat(report.status()).isEqualTo(ReportStatus.CLOSED);
      assertThat(report.claimable()).isFalse();
      assertThat(report.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("重复关闭幂等：第二次返回 false（重试常态，不是错误）")
    void closeIsIdempotent() {
      ClaimReport report = open();
      report.close();

      assertThat(report.close()).isFalse();
      assertThat(report.status()).isEqualTo(ReportStatus.CLOSED);
    }

    @Test
    @DisplayName("status 为 null 的历史行按 REPORTED 解释（兼容回填行）")
    void nullStatusTreatedAsReported() {
      ClaimReport report = open();
      ReflectionTestUtils.setField(report, "status", null);

      assertThat(report.status()).isEqualTo(ReportStatus.REPORTED);
      assertThat(report.claimable()).isTrue();
    }

    @Test
    @DisplayName("状态枚举的 name 与库里存的值一致（DB 存 name，不存序号）")
    void statusStoredAsEnumName() {
      ClaimReport report = open();

      assertThat(report.getStatus()).isEqualTo("REPORTED");
      assertThat(ReportStatus.ofName("CLOSED")).isEqualTo(ReportStatus.CLOSED);
      assertThat(ReportStatus.CLOSED.displayName()).isNotBlank();
    }
  }
}
