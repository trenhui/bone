package com.bone.procurement.contract.domain.model.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 审查聚合状态机：合法迁移可达快照，非法迁移一律拒绝。 */
class ContractReviewTest {

  @Test
  void 合法迁移解析到快照必经人工复核() {
    ContractReview review = ContractReview.pending("rv-1", "CT-001");
    assertThat(review.status()).isEqualTo(ReviewStatus.PENDING);
    assertThat(review.aiParticipation()).isEqualTo(AiParticipation.UNKNOWN);
    assertThat(review.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);

    review.markParsed();
    assertThat(review.status()).isEqualTo(ReviewStatus.PARSED);

    review.startReview();
    assertThat(review.status()).isEqualTo(ReviewStatus.IN_REVIEW);

    review.routeToHuman("AI 建议待人工终决");
    assertThat(review.status()).isEqualTo(ReviewStatus.HUMAN_REVIEW);

    // 真实链路：人工终决留痕后方可快照（纵深断言，SUGGESTION-6）。
    review.recordHumanOverride(
        new HumanOverride("reviewer-1", "人工确认", 0.42, RiskLevel.LOW, RiskLevel.HIGH));
    review.snapshot(ReviewSnapshotRef.of("snap://rv-1"));
    assertThat(review.status()).isEqualTo(ReviewStatus.SNAPSHOTTED);
    assertThat(review.reviewSnapshotRef().ref()).isEqualTo("snap://rv-1");
  }

  @Test
  void 未经人工终决不得快照_纵深断言() {
    ContractReview review = ContractReview.pending("rv-1c", "CT-001");
    review.markParsed();
    review.routeToHuman("AI 建议待人工终决");

    assertThatThrownBy(() -> review.snapshot(ReviewSnapshotRef.of("snap://rv-1c")))
        .as("仅 routeToHuman 而未 recordHumanOverride，不得进入快照终态")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("人工终决");
    assertThat(review.status()).isEqualTo(ReviewStatus.HUMAN_REVIEW);
  }

  @Test
  void IN_REVIEW直达SNAPSHOTTED非法_不得绕过人工() {
    ContractReview review = ContractReview.pending("rv-1b", "CT-001");
    review.markParsed();
    review.startReview();

    assertThatThrownBy(() -> review.snapshot(ReviewSnapshotRef.of("snap://rv-1b")))
        .as("IN_REVIEW → SNAPSHOTTED 是绕过人工终决的后门，必须关闭")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("IN_REVIEW")
        .hasMessageContaining("SNAPSHOTTED");
    assertThat(review.status()).isEqualTo(ReviewStatus.IN_REVIEW);
  }

  @Test
  void 解析失败failClosed转人工且不产风险结论() {
    ContractReview review = ContractReview.pending("rv-2", "CT-002");
    review.markParseFailed("encrypted pdf");

    assertThat(review.status()).isEqualTo(ReviewStatus.FAILED);
    assertThat(review.failureReason()).contains("encrypted");
    assertThat(review.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
    assertThat(review.riskItems()).isEmpty();

    review.routeToHuman("解析失败转人工");
    assertThat(review.status()).isEqualTo(ReviewStatus.HUMAN_REVIEW);
    assertThat(review.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
  }

  @Test
  void 非法迁移被拒绝() {
    ContractReview pending = ContractReview.pending("rv-3", "CT-003");
    assertThatThrownBy(pending::startReview)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PENDING");

    ContractReview parsed = ContractReview.pending("rv-4", "CT-004");
    parsed.markParsed();
    assertThatThrownBy(() -> parsed.snapshot(ReviewSnapshotRef.of("snap://rv-4")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PARSED");

    ContractReview failed = ContractReview.pending("rv-5", "CT-005");
    failed.markParseFailed("ocr unreadable");
    assertThatThrownBy(failed::markParsed)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FAILED");

    ContractReview done = ContractReview.pending("rv-6", "CT-006");
    done.markParsed();
    done.startReview();
    done.routeToHuman("人工终决");
    done.recordHumanOverride(
        new HumanOverride("reviewer-6", "人工终决留痕", 0.31, RiskLevel.HIGH, RiskLevel.LOW));
    done.snapshot(ReviewSnapshotRef.of("snap://rv-6"));
    assertThatThrownBy(() -> done.routeToHuman("too late"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SNAPSHOTTED");
  }
}
