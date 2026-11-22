package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.appeal.AppealRecord;
import com.bone.claim.domain.model.appeal.AppealReviewResult;
import com.bone.claim.domain.model.appeal.AppealStatus;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.repository.AppealRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * P0-C 判别力测试：申诉窗口统一为 14 天，且复议状态回流按对客结论落回案件终态/核赔中。
 *
 * <p>变异验证：把 APPEAL_WINDOW 改回 48h ⇒ T+47h 通过但超时用例拿不到 SC_CLAIM_APPEAL_EXPIRED；把 detail 改回仅案件号 ⇒
 * 对客口径断言红； 把 UPHELD/REOPEN 分支对调 ⇒ 状态回流用例红。
 */
class AppealWindowContractTest {

  private static final long CLAIM = 7001L;
  private static final long TENANT = 7L;

  @Test
  @DisplayName("T+47h 申诉成功（旧 48h 边界仍在窗口内；第 3 天申诉不得被拒）")
  void appealAt47HoursAccepted() {
    AppealRecord appeal =
        AppealRecord.submit(
            1L,
            TENANT,
            "AP-47H",
            CLAIM,
            "CL-47H",
            "CLOSED_REJECTED",
            "customer",
            Instant.now().minus(Duration.ofHours(47)));

    assertThat(appeal.statusEnum()).isEqualTo(AppealStatus.SUBMITTED);
    assertThat(appeal.getCaseClosedAt()).isNotNull();
  }

  @Test
  @DisplayName("T+15 天 → 应用层抛 SC_CLAIM_APPEAL_EXPIRED（422），detail 带对客口径而非裸案件号")
  void appealAt15DaysRejectedWithBusinessCode() {
    AppealApplicationService service = service();
    ClaimCase claim = claimAt(ClaimStatus.CLOSED_REJECTED, daysAgo(15));
    // 真实聚合不能用 when() 打桩（Mockito 会把上一次 mock 交互误当被桩对象）。
    ReflectionTestUtils.setField(claim, "claimNo", "CL-15D");
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);

    assertThatThrownBy(() -> service.submit(CLAIM, "customer", "第 15 天申诉"))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex -> {
              BizException biz = (BizException) ex;
              assertThat(biz.getErrorCode()).isEqualTo(ClaimErrorCodes.CLAIM_APPEAL_EXPIRED);
              assertThat(biz.getMessage()).contains("10 个工作日");
              assertThat(biz.getMessage()).contains("14 个自然日");
            });
  }

  @Test
  @DisplayName("复议 UPHELD → CLOSED_REJECTED；REOPEN → ADJUSTING（案件真实状态回流）")
  void reviewReflowsCaseStatus() {
    AppealApplicationService service = service();
    ClaimCase upheld = appealingClaim();
    when(appealRepository.findLatestByClaimId(CLAIM)).thenReturn(submittedAppeal("AP-UPHELD"));
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(upheld);

    service.review(CLAIM, AppealReviewResult.UPHELD, "reviewer", "维持");

    assertThat(upheld.status()).isEqualTo(ClaimStatus.CLOSED_REJECTED);

    ClaimCase reopened = appealingClaim();
    when(appealRepository.findLatestByClaimId(CLAIM)).thenReturn(submittedAppeal("AP-REOPEN"));
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(reopened);

    service.review(CLAIM, AppealReviewResult.REOPEN, "reviewer", "重启");

    assertThat(reopened.status()).isEqualTo(ClaimStatus.ADJUSTING);
  }

  @Test
  @DisplayName("重复 review 被拒（DECIDED 不可再复议）")
  void duplicateReviewRejected() {
    AppealRecord appeal = submittedAppeal("AP-DUP");
    appeal.review(AppealReviewResult.UPHELD, "r1", "first");

    assertThatThrownBy(() -> appeal.review(AppealReviewResult.REOPEN, "r2", "second"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("仅受理中申诉可复议");
    assertThat(appeal.reviewResultEnum()).isEqualTo(AppealReviewResult.UPHELD);
  }

  // ===================== 内部 =====================

  private static Instant daysAgo(int days) {
    return Instant.now().minus(Duration.ofDays(days));
  }

  private static AppealRecord submittedAppeal(String no) {
    return AppealRecord.submit(
        1L, TENANT, no, CLAIM, "CL-" + no, "CLOSED_REJECTED", "customer", daysAgo(1));
  }

  private static ClaimCase claimAt(ClaimStatus status, Instant closedAt) {
    ClaimCase claim = draft();
    ReflectionTestUtils.setField(claim, "status", status.name());
    ReflectionTestUtils.setField(claim, "closedAt", closedAt);
    return claim;
  }

  private static ClaimCase appealingClaim() {
    return claimAt(ClaimStatus.APPEALING, daysAgo(1));
  }

  private static ClaimCase draft() {
    return ClaimCase.draft(
        CLAIM,
        TENANT,
        9001L,
        "EG20260001",
        7L,
        "hash-abc",
        "OUTPATIENT",
        "04400001",
        "12345678",
        Instant.parse("2026-03-12T10:00:00Z"),
        "B20260312",
        "idem-appeal-window-" + System.nanoTime(),
        "v3",
        List.of(
            ClaimItem.of(
                2001L,
                1L,
                CLAIM,
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"))));
  }

  private final ClaimCaseApplicationService claimCaseApplicationService =
      mock(ClaimCaseApplicationService.class);
  private final AppealRepository appealRepository = mock(AppealRepository.class);
  private final TenantPort tenantPort = mock(TenantPort.class);
  private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);

  private AppealApplicationService service() {
    return new AppealApplicationService(
        appealRepository, claimCaseApplicationService, tenantPort, domainEventPublisher);
  }
}
