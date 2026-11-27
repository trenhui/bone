package com.bone.procurement.governance.domain.model.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 治理态势汇总判词：三态语义，仅需关注态可操作，不可判定不得被当作通过。 */
class PostureVerdictTest {

  @Test
  void 三态均存在且语义明确() {
    assertThat(PostureVerdict.values())
        .containsExactly(
            PostureVerdict.UNDETERMINED,
            PostureVerdict.ALL_CLEAR,
            PostureVerdict.ATTENTION_REQUIRED);
  }

  @Test
  void 仅需关注态可操作() {
    assertThat(PostureVerdict.UNDETERMINED.isActionable()).isFalse();
    assertThat(PostureVerdict.ALL_CLEAR.isActionable()).isFalse();
    assertThat(PostureVerdict.ATTENTION_REQUIRED.isActionable()).isTrue();
  }
}
