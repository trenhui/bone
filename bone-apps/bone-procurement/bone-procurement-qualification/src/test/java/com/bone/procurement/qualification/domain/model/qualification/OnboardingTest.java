package com.bone.procurement.qualification.domain.model.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 准入不变量：AI 评分不得直接终决，终决必须具名人工。 */
class OnboardingTest {

  @Test
  @DisplayName("AI 高分也不得自动 APPROVED，必须人工终决")
  void aiScoreCannotApprove() {
    Onboarding o = Onboarding.start(1L, "SUP-1", "ONB-1", "bg-ref", new BigDecimal("99.9"));
    o.submitForReview();
    assertThatThrownBy(() -> o.approveByHuman(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("人工");
    o.approveByHuman("human-1");
    assertThat(o.status()).isEqualTo(OnboardingStatus.APPROVED);
    assertThat(o.getApprovedBy()).isEqualTo("human-1");
  }

  @Test
  @DisplayName("终态不可回写，非法迁移拒绝")
  void terminalStateIsImmutable() {
    Onboarding o = Onboarding.start(1L, "SUP-2", "ONB-2", "bg-ref", BigDecimal.TEN);
    o.submitForReview();
    o.rejectByHuman("human-2");
    assertThatThrownBy(o::submitForReview)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非法准入状态迁移");
  }
}
