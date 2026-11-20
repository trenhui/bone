package com.bone.procurement.qualification.domain.model.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 证照不变量：过期不得作准入依据、OCR 须人工复核。 */
class QualificationTest {

  private Date future() {
    return new Date(System.currentTimeMillis() + 86400000L);
  }

  @Test
  @DisplayName("OCR 结果须具名人工复核，OCR 不得自证")
  void ocrRequiresHumanReview() {
    Qualification q =
        Qualification.submit(
            1L, "SUP-1", "BUSINESS_LICENSE", "MASKED-001", future(), "att-1", "ocr-hit-true");
    assertThat(q.status()).isEqualTo(VerifyStatus.PENDING);
    assertThatThrownBy(() -> q.reviewByHuman("  ", true, new Date()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("人工复核");
    q.reviewByHuman("reviewer-1", true, new Date());
    assertThat(q.status()).isEqualTo(VerifyStatus.VERIFIED);
    assertThat(q.getReviewedBy()).isEqualTo("reviewer-1");
  }

  @Test
  @DisplayName("过期证照不得复核通过，巡检标记 EXPIRED 后不可回写")
  void expiredCertCannotSupportOnboarding() {
    Date past = new Date(System.currentTimeMillis() - 1000L);
    Qualification q = Qualification.submit(1L, "SUP-2", "ISO", "MASKED-002", past, "att-2", "{}");
    assertThatThrownBy(() -> q.reviewByHuman("reviewer-1", true, new Date()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("过期");
    q.markExpiredIfDue(new Date());
    assertThat(q.status()).isEqualTo(VerifyStatus.EXPIRED);
    assertThat(q.status().canSupportOnboarding()).isFalse();
    assertThatThrownBy(() -> q.reviewByHuman("reviewer-1", true, new Date()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("终态");
  }
}
