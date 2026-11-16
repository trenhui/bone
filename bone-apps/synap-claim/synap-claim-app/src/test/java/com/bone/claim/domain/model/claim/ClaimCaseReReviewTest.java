package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 批3 独立复审状态机钉子：43/46 与 44 并列、43→46 流转、同人不可自审。 */
class ClaimCaseReReviewTest {

  private static final Instant OUT_INSURE = Instant.parse("2026-03-12T10:00:00Z");

  private static ClaimCase newClaim() {
    return ClaimCase.draft(
        1001L,
        42L,
        "EG20260001",
        7L,
        "hash-abc",
        "OUTPATIENT",
        "04400001",
        "12345678",
        OUT_INSURE,
        "B20260312",
        "idem-key-rereview-0",
        "v3",
        List.of(
            ClaimItem.of(
                2001L,
                1L,
                1001L,
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"))));
  }

  /** 推进到 42 已审核（初审人 = reviewer-1）。 */
  private static ClaimCase adjustedClaim() {
    ClaimCase claim = newClaim();
    claim.accept();
    claim.allocateToPreExam("op-1");
    claim.startPreExam(false, "op-1");
    claim.completePreExam("op-1");
    claim.escalateToAdjust("op-1");
    claim.claimAdjustTask("reviewer-1");
    claim.decide(Decision.APPROVED, "reviewer-1", null, null, new BigDecimal("10.00"));
    assertThat(claim.status()).isEqualTo(ClaimStatus.ADJUSTED);
    return claim;
  }

  @Test
  @DisplayName("1.1 三状态共存：43/44/46 都在状态字典且对外都投影 REVIEWING")
  void threeStatesCoexist() {
    ClaimStatus pending = ClaimStatus.ofCode("43");
    ClaimStatus reviewing = ClaimStatus.ofCode("44");
    ClaimStatus passed = ClaimStatus.ofCode("46");

    assertThat(pending).isEqualTo(ClaimStatus.RE_REVIEW_PENDING);
    assertThat(reviewing).isEqualTo(ClaimStatus.REVIEWING);
    assertThat(passed).isEqualTo(ClaimStatus.RE_REVIEW_PASSED);

    // 三者互不相同——「合并写法」（把 43/46 合并进 44）变异会在此判红
    assertThat(pending).isNotEqualTo(reviewing);
    assertThat(passed).isNotEqualTo(reviewing);
    assertThat(pending).isNotEqualTo(passed);

    for (ClaimStatus status : List.of(pending, reviewing, passed)) {
      assertThat(status.toExternal()).isEqualTo("REVIEWING");
      assertThat(status.reviewing()).isTrue();
    }
  }

  @Test
  @DisplayName("1.1 流转：42→43→46 独立复审通过；43 不可空跃迁")
  void reReviewTransition() {
    ClaimCase claim = adjustedClaim();
    claim.submitForIndependentReReview("reviewer-2");
    assertThat(claim.status()).isEqualTo(ClaimStatus.RE_REVIEW_PENDING);
    assertThat(claim.stageEnum().name()).isEqualTo("REVIEWING");

    claim.completeIndependentReReview(true, "reviewer-2");
    // 必须是独立的 46，而不是回落到 44 或 50——合并写法/复用终态变异在此判红
    assertThat(claim.status()).isEqualTo(ClaimStatus.RE_REVIEW_PASSED);
    assertThat(claim.status()).isNotEqualTo(ClaimStatus.REVIEWING);
    assertThat(claim.status()).isNotEqualTo(ClaimStatus.FINISHED);
  }

  @Test
  @DisplayName("1.1 复审驳回：43→45 拒赔生效")
  void reReviewReject() {
    ClaimCase claim = adjustedClaim();
    claim.submitForIndependentReReview("reviewer-2");
    claim.completeIndependentReReview(false, "reviewer-2");
    assertThat(claim.status()).isEqualTo(ClaimStatus.CLOSED_REJECTED);
  }

  @Test
  @DisplayName("3.2 同人不可自审：初审人提交独立复审被拒且状态不变")
  void selfReReviewRejected() {
    ClaimCase claim = adjustedClaim();
    assertThat(claim.getFirstReviewerId()).isEqualTo("reviewer-1");

    assertThatThrownBy(() -> claim.submitForIndependentReReview("reviewer-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("非初审人");
    assertThat(claim.status()).isEqualTo(ClaimStatus.ADJUSTED);
  }

  @Test
  @DisplayName("3.2 同人不可自审：同人守卫不因大小写/空白绕过")
  void selfReReviewGuardStrict() {
    ClaimCase claim = adjustedClaim();
    assertThatThrownBy(() -> claim.submitForIndependentReReview(" reviewer-1 "))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("非初审人");
    assertThat(claim.status()).isEqualTo(ClaimStatus.ADJUSTED);
  }

  @Test
  @DisplayName("1.1 只有 42 可提交独立复审；44 复核中不可重复进入")
  void onlyAdjustedCanSubmit() {
    ClaimCase claim = adjustedClaim();
    claim.submitForSecondReview("reviewer-2");
    assertThat(claim.status()).isEqualTo(ClaimStatus.REVIEWING);
    assertThatThrownBy(() -> claim.submitForIndependentReReview("reviewer-3"))
        .isInstanceOf(DomainException.class);
    assertThat(claim.status()).isEqualTo(ClaimStatus.REVIEWING);
  }

  @Test
  @DisplayName("1.1 只有 43 可提交复审结论")
  void onlyPendingCanComplete() {
    ClaimCase claim = adjustedClaim();
    assertThatThrownBy(() -> claim.completeIndependentReReview(true, "reviewer-2"))
        .isInstanceOf(DomainException.class);
    assertThat(claim.status()).isEqualTo(ClaimStatus.ADJUSTED);
  }
}
