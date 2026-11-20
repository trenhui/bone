package com.bone.procurement.contract.domain.model.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.domain.service.ReviewEventAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ContractReviewed 事件契约：payload 严格 7 字段，风险明细 / output 原文 / PII 一律不进事件。 */
class ContractReviewedEventTest {

  private static final List<String> EXPECTED_FIELDS =
      List.of(
          "reviewId",
          "contractNo",
          "riskLevel",
          "riskItemsHash",
          "reviewSnapshotRef",
          "aiAvailable",
          "modelVersionSnapshot");

  @Test
  void 事件payload严格锁定7字段() {
    assertThat(
            Arrays.stream(ContractReviewedEvent.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactlyElementsOf(EXPECTED_FIELDS);
    assertThat(ContractReviewedEvent.class.getDeclaredFields()).hasSize(7);

    for (Field field : ContractReviewedEvent.class.getDeclaredFields()) {
      String name = field.getName().toLowerCase();
      assertThat(name)
          .as("事件不得含 output / 正文 / PII 字段")
          .doesNotContain("output")
          .doesNotContain("content")
          .doesNotContain("pii")
          .doesNotContain("text")
          .doesNotContain("raw");
    }
  }

  @Test
  void PII注入不得出现在事件序列化结果() throws Exception {
    String idCard = "110101199003077777";
    ContractReview review = reviewWithBasis("依据含身份证 " + idCard + " 的原文");

    ContractReviewedEvent event = ReviewEventAssembler.assemble(review);
    String json = new ObjectMapper().writeValueAsString(event);

    assertThat(json).doesNotContain(idCard).doesNotContain("依据含身份证");
    assertThat(event.riskItemsHash()).isNotBlank();
    assertThat(event.reviewSnapshotRef()).isEqualTo("snap://rv-pii");
    assertThat(event.aiAvailable()).isEqualTo(AiParticipation.AVAILABLE);
    assertThat(event.riskLevel()).isEqualTo(RiskLevel.HIGH);
  }

  @Test
  void 风险明细变化改变摘要哈希() {
    String hashA = ReviewEventAssembler.assemble(reviewWithBasis("依据A")).riskItemsHash();
    String hashB = ReviewEventAssembler.assemble(reviewWithBasis("依据B")).riskItemsHash();
    assertThat(hashA).isNotEqualTo(hashB);
  }

  private static ContractReview reviewWithBasis(String basis) {
    ContractReview review = ContractReview.pending("rv-pii", "CT-PII");
    review.markParsed();
    review.startReview();
    review.applyAiSuggestion(
        new ReviewSuggestion(
            AiParticipation.AVAILABLE,
            RiskLevel.HIGH,
            List.of(new RiskItem("第一条", 1, 0, 20, RiskLevel.HIGH, basis, 0.95)),
            0.95,
            false,
            "model-v1"));
    review.routeToHuman("PII 注入复核");
    review.recordHumanOverride(
        new HumanOverride("reviewer-pii", "人工终决", 0.95, RiskLevel.HIGH, RiskLevel.HIGH));
    review.snapshot(ReviewSnapshotRef.of("snap://rv-pii"));
    return review;
  }
}
