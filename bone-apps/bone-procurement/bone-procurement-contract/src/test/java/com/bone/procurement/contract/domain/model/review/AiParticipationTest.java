package com.bone.procurement.contract.domain.model.review;

import static com.bone.procurement.contract.domain.model.review.AiParticipation.AVAILABLE;
import static com.bone.procurement.contract.domain.model.review.AiParticipation.UNAVAILABLE;
import static com.bone.procurement.contract.domain.model.review.AiParticipation.UNDETERMINED;
import static com.bone.procurement.contract.domain.model.review.AiParticipation.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** AI 参与三态门禁：UNKNOWN / UNDETERMINED 不得被压成 AVAILABLE=false 或「AI 未参与」（AI-4）。 */
class AiParticipationTest {

  @Test
  void 契约枚举值为四态() {
    assertThat(AiParticipation.values())
        .containsExactly(AVAILABLE, UNAVAILABLE, UNKNOWN, UNDETERMINED);
  }

  @Test
  void 未知态不得被压成AI未参与() {
    for (AiParticipation participation : List.of(UNKNOWN, UNDETERMINED)) {
      assertThat(participation.isCannotConfirm()).as(participation + " 必须是无法确认").isTrue();
      assertThat(participation.isAiConfirmed()).as(participation + " 不得被当作已确认参与").isFalse();
      assertThat(participation.isAiNotParticipated()).as(participation + " 不得被压成 AI 未参与").isFalse();
    }
  }

  @Test
  void 未参与和已参与各只有唯一取值() {
    assertThat(UNAVAILABLE.isAiNotParticipated()).isTrue();
    assertThat(UNAVAILABLE.isCannotConfirm()).isFalse();
    assertThat(AVAILABLE.isAiConfirmed()).isTrue();
    assertThat(AVAILABLE.isAiNotParticipated()).isFalse();
  }

  @Test
  void 未知态与未参与是不同的原始码() {
    assertThat(UNKNOWN).isNotEqualTo(UNAVAILABLE);
    assertThat(UNDETERMINED).isNotEqualTo(UNAVAILABLE);
    assertThat(UNKNOWN.disclosureCode()).isEqualTo("UNKNOWN");
    assertThat(UNDETERMINED.disclosureCode()).isEqualTo("UNDETERMINED");
  }
}
