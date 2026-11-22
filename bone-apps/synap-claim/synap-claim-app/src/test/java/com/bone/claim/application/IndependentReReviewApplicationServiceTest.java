package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 独立复审应用服务判据钉子（批 3 任务 3.1 / 3.2 / 4.1）。
 *
 * <p>钉四件事：① RE_REVIEW 关闭零流转、44 不受影响；② 同人不可自审翻译成可辨认码且状态不变、不落库；③ 复审队列只出 43 状态案件且按持权人过滤；④
 * 跨租户由仓储隔离（传给仓储的 tenantId 是当前租户）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndependentReReviewApplicationServiceTest {

  private static final long TENANT = 42L;
  private static final long CLAIM_ID = 1001L;
  private static final Instant NOW = Instant.parse("2026-03-12T10:00:00Z");

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimCaseApplicationService claimCaseApplicationService;
  @Mock private FeatureTenantToggleService toggleService;
  @Mock private TenantPort tenantPort;

  private IndependentReReviewApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new IndependentReReviewApplicationService(
            claimCaseRepository, claimCaseApplicationService, toggleService, tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(toggleService.isFeatureAllowed(eq(TENANT), eq(true), eq(FeatureToggle.FEATURE_RE_REVIEW)))
        .thenReturn(true);
  }

  private static ClaimCase adjustedClaim() {
    ClaimCase claim =
        ClaimCase.draft(
            TENANT,
            42L,
            9001L,
            "EG20260001",
            7L,
            "hash-rereview",
            "OUTPATIENT",
            "04400001",
            "12345678",
            NOW,
            "B20260312",
            "idem-rereview-app",
            "v3",
            List.of(
                ClaimItem.of(
                    2001L,
                    1L,
                    TENANT,
                    "诊查费",
                    "",
                    ItemCategory.SERVICE,
                    BigDecimal.ONE,
                    BigDecimal.TEN,
                    new BigDecimal("10.00"))));
    claim.accept();
    claim.allocateToPreExam("op-1");
    claim.startPreExam(false, "op-1");
    claim.completePreExam("op-1");
    claim.escalateToAdjust("op-1");
    claim.claimAdjustTask("reviewer-1");
    claim.decide(Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("10.00"));
    return claim;
  }

  @Test
  @DisplayName("4.1 RE_REVIEW 关闭时零流转：submit 被拒、状态不变、不落库")
  void featureOffBlocksSubmit() {
    when(toggleService.isFeatureAllowed(eq(TENANT), eq(true), eq(FeatureToggle.FEATURE_RE_REVIEW)))
        .thenReturn(false);
    ClaimCase claim = adjustedClaim();
    when(claimCaseRepository.findById(CLAIM_ID)).thenReturn(claim);

    assertThatThrownBy(() -> service.submitForReReview(CLAIM_ID, "reviewer-2"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("RE_REVIEW");

    assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ADJUSTED.name());
    verify(claimCaseApplicationService, never()).saveClaim(any());
  }

  @Test
  @DisplayName("3.2 同人不可自审：初审人提交独立复审被拒，映射可辨认码，状态不变不落库")
  void selfReReviewRejectedWithDistinctCode() {
    ClaimCase claim = adjustedClaim();
    when(claimCaseRepository.findById(CLAIM_ID)).thenReturn(claim);

    assertThatThrownBy(() -> service.submitForReReview(CLAIM_ID, " reviewer-1 "))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.SC_CLAIM_REREVIEWER_CONFLICT));

    assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ADJUSTED.name());
    verify(claimCaseApplicationService, never()).saveClaim(any());
  }

  @Test
  @DisplayName("3.1 提交成功：42→43 记录独立复审人并落库一次")
  void submitPersistsAndTransitions() {
    ClaimCase claim = adjustedClaim();
    when(claimCaseRepository.findById(CLAIM_ID)).thenReturn(claim);

    service.submitForReReview(CLAIM_ID, "reviewer-2");

    assertThat(claim.getStatus()).isEqualTo(ClaimStatus.RE_REVIEW_PENDING.name());
    assertThat(claim.getSecondReviewerId()).isEqualTo("reviewer-2");
    verify(claimCaseApplicationService, org.mockito.Mockito.times(1)).saveClaim(claim);
  }

  @Test
  @DisplayName("3.1 复审队列：只出 43 状态案件、按持权人过滤，并把当前租户传给仓储（跨租户不可见）")
  void queueFiltersByTenantStatusAndAssignee() {
    ClaimCase pending = adjustedClaim();
    pending.submitForIndependentReReview("reviewer-2");
    when(claimCaseRepository.findReReviewQueue(eq(TENANT), eq("reviewer-2"), anyInt(), anyInt()))
        .thenReturn(List.of(pending));
    when(claimCaseRepository.countReReviewQueue(eq(TENANT), eq("reviewer-2"))).thenReturn(1L);

    PageResult<IndependentReReviewApplicationService.ReReviewQueueRow> page =
        service.queue("reviewer-2", 1, 20);

    assertThat(page.getRecords()).hasSize(1);
    assertThat(page.getTotal()).isEqualTo(1L);
    assertThat(page.getRecords().get(0).status()).isEqualTo(ClaimStatus.RE_REVIEW_PENDING.name());
    verify(claimCaseRepository).findReReviewQueue(eq(TENANT), eq("reviewer-2"), anyInt(), anyInt());
    verify(claimCaseRepository).countReReviewQueue(eq(TENANT), eq("reviewer-2"));
  }
}
