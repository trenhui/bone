package com.bone.procurement.contract.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.domain.model.AiParticipation;
import com.bone.procurement.contract.domain.model.ContractReview;
import com.bone.procurement.contract.domain.model.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DISCLOSE-01/02 与 AI-4：未知态原样返回、显示「无法确认」，版本快照不得冒充参与证据。
 *
 * <p>评测符号：undetermined_notRenderedAsNotInvolved / modelVersion_notTreatedAsEvidence。
 */
class ProcuraAiDisclosureTest {

  private final AiDisclosureService service = new AiDisclosureService();

  @Test
  void undetermined_notRenderedAsNotInvolved() {
    AiDisclosureView view = service.disclose(reviewWith(AiParticipation.UNDETERMINED, null));

    assertThat(view.aiAvailableCode()).as("后端返回原始码，不翻译、不降级").isEqualTo("UNDETERMINED");
    assertThat(view.disclosureText()).isEqualTo("无法确认");
    assertThat(view.disclosureText()).as("UNDETERMINED 绝不能被渲染成「AI 未参与」").isNotEqualTo("AI 未参与");
    assertThat(view.canConfirm()).isFalse();
  }

  @Test
  void modelVersion_notTreatedAsEvidence() {
    AiDisclosureView view =
        service.disclose(reviewWith(AiParticipation.UNKNOWN, "model-v9-internal"));

    assertThat(view.modelVersionSnapshot()).isEqualTo("model-v9-internal");
    assertThat(view.aiAvailableCode()).isEqualTo("UNKNOWN");
    assertThat(view.disclosureText()).isEqualTo("无法确认");
    assertThat(view.canConfirm()).as("版本快照有值也不构成 AI 参与证据").isFalse();
  }

  @Test
  void 三态文案与确认标志() {
    assertThat(service.disclose(reviewWith(AiParticipation.AVAILABLE, "m1")).canConfirm()).isTrue();
    assertThat(service.disclose(reviewWith(AiParticipation.AVAILABLE, "m1")).disclosureText())
        .isEqualTo("AI 已参与");
    AiDisclosureView notInvolved = service.disclose(reviewWith(AiParticipation.UNAVAILABLE, null));
    assertThat(notInvolved.disclosureText()).isEqualTo("AI 未参与");
    assertThat(notInvolved.canConfirm()).isFalse();
  }

  private static ContractReview reviewWith(AiParticipation participation, String modelVersion) {
    ContractReview review = ContractReview.pending("rv-d", "CT-D");
    review.markParsed();
    review.startReview();
    review.applyAiSuggestion(
        new ReviewSuggestion(
            participation, RiskLevel.UNKNOWN, List.of(), 0.0, false, modelVersion));
    return review;
  }
}
