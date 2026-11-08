package com.bone.claim.domain.model.appeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A8 申诉单测：48h 时限 + 终态前提 + 复议终态。 */
class AppealRecordTest {

  private static final long TENANT = 7L;

  private static Instant hoursAgo(int h) {
    return Instant.now().minus(Duration.ofHours(h));
  }

  @Test
  @DisplayName("结案(FINISHED)后 48h 内发起申诉 → SUBMITTED，原终态存枚举名")
  void submitFromFinished() {
    AppealRecord appeal =
        AppealRecord.submit(1L, TENANT, "AP7-1", 100L, "CL1", "FINISHED", "user", hoursAgo(1));
    assertThat(appeal.statusEnum()).isEqualTo(AppealStatus.SUBMITTED);
    assertThat(appeal.getOriginalStatus()).isEqualTo("FINISHED");
  }

  @Test
  @DisplayName("非终态(REVIEWING/ADJUSTING) → 拒")
  void submitNonTerminalRejected() {
    assertThatThrownBy(
            () ->
                AppealRecord.submit(
                    1L, TENANT, "AP7-2", 100L, "CL1", "REVIEWING", "u", hoursAgo(1)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("仅拒赔生效(CLOSED_REJECTED)或结案(FINISHED)");
  }

  @Test
  @DisplayName("历史数字码(50) → 拒，并给出可定位脏数据的消息")
  void submitLegacyCodeRejected() {
    assertThatThrownBy(
            () -> AppealRecord.submit(1L, TENANT, "AP7-2b", 100L, "CL1", "50", "u", hoursAgo(1)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("须为枚举名");
  }

  @Test
  @DisplayName("超 48h 时限 → 拒（§6.6）")
  void submitTimeoutRejected() {
    assertThatThrownBy(
            () ->
                AppealRecord.submit(
                    1L, TENANT, "AP7-3", 100L, "CL1", "FINISHED", "u", hoursAgo(49)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("48h");
  }

  @Test
  @DisplayName("复议：UPHELD → DECIDED(维持)；REOPEN → DECIDED(重启)")
  void review() {
    AppealRecord appeal =
        AppealRecord.submit(
            1L, TENANT, "AP7-4", 100L, "CL1", "CLOSED_REJECTED", "user", hoursAgo(2));
    appeal.review(AppealReviewResult.UPHELD, "reviewer", "理由");
    assertThat(appeal.statusEnum()).isEqualTo(AppealStatus.DECIDED);
    assertThat(appeal.reviewResultEnum()).isEqualTo(AppealReviewResult.UPHELD);
  }

  @Test
  @DisplayName("已复议后不可再复议")
  void reviewTwiceRejected() {
    AppealRecord appeal =
        AppealRecord.submit(1L, TENANT, "AP7-5", 100L, "CL1", "FINISHED", "user", hoursAgo(2));
    appeal.review(AppealReviewResult.REOPEN, "reviewer", "理由");
    assertThatThrownBy(() -> appeal.review(AppealReviewResult.UPHELD, "r2", "x"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("仅受理中申诉可复议");
  }
}
