package com.bone.aidoc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** ReviewFeedback 闭环核心：区分「自动放行却被判错」与其它情形。 */
class ReviewFeedbackTest {

  private static ReviewFeedback fb(
      ReviewStatus decidedBy, boolean correct, String engineValue, String humanValue) {
    return new ReviewFeedback(
        "d1",
        "t1",
        "total_amount",
        engineValue,
        humanValue,
        correct,
        decidedBy,
        "ref",
        Instant.now());
  }

  @Test
  void confirmed_error_is_auto_pass_but_wrong() {
    ReviewFeedback fb = fb(ReviewStatus.AUTO_PASSED, false, "200.00", "100.00");
    assertThat(fb.isConfirmedError()).isTrue();
  }

  @Test
  void auto_pass_that_was_correct_is_not_an_error() {
    ReviewFeedback fb = fb(ReviewStatus.AUTO_PASSED, true, "100.00", "100.00");
    assertThat(fb.isConfirmedError()).isFalse();
  }

  @Test
  void manual_review_outcome_is_not_a_confirmed_error() {
    assertThat(fb(ReviewStatus.MANUAL_REVIEW, false, "x", "y").isConfirmedError()).isFalse();
    assertThat(fb(ReviewStatus.SECONDARY_RECOGNITION, false, "x", "y").isConfirmedError())
        .isFalse();
  }
}
