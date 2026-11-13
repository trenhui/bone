package com.bone.masterdata.domain.model.changerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.masterdata.domain.model.changerequest.ChangeRequest.ApprovalState;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.Segment;
import com.bone.masterdata.domain.model.changerequest.ChangeRequest.Status;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 分段并行 CR 领域内核纯单测：不依赖 Spring / DB，逐条守住终局硬约束。 */
class ChangeRequestTest {

  private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");
  private static final String CREATOR = "101";
  private static final String OWNER_GENERAL = "201";
  private static final String OWNER_CC = "202";
  private static final String OWNER_PO = "203";
  private static final String APPLIER = "301";

  private static Map<Segment, String> owners() {
    Map<Segment, String> m = new LinkedHashMap<>();
    m.put(Segment.GENERAL, OWNER_GENERAL);
    m.put(Segment.COMPANY_CODE, OWNER_CC);
    m.put(Segment.PURCHASING_ORG, OWNER_PO);
    return m;
  }

  private static ChangeRequest draft() {
    return ChangeRequest.create("CR-1", 1L, "P-1", 7L, "新增供应商开户行", CREATOR, T0, owners());
  }

  private static ChangeRequest inReview() {
    return draft().submit(CREATOR, T0.plusSeconds(1));
  }

  private static ChangeRequest approved() {
    return inReview()
        .decide(Segment.PURCHASING_ORG, ApprovalState.APPROVED, OWNER_PO, T0.plusSeconds(2), null)
        .decide(Segment.GENERAL, ApprovalState.APPROVED, OWNER_GENERAL, T0.plusSeconds(3), null)
        .decide(Segment.COMPANY_CODE, ApprovalState.APPROVED, OWNER_CC, T0.plusSeconds(4), null);
  }

  @Test
  @DisplayName("三段 specialist 路由缺一即拒绝创建（T4 终局一致性探针）")
  void missingSegmentRouteShouldBeRejected() {
    Map<Segment, String> partial = owners();
    partial.remove(Segment.COMPANY_CODE);
    assertThatThrownBy(
            () -> ChangeRequest.create("CR-1", 1L, "P-1", 7L, "说明", CREATOR, T0, partial))
        .hasMessageContaining("三段 specialist 路由必须齐全");
  }

  @Test
  @DisplayName("分段 Owner 为空即拒绝，禁止匿名路由")
  void blankOwnerShouldBeRejected() {
    Map<Segment, String> m = owners();
    m.put(Segment.GENERAL, "  ");
    assertThatThrownBy(() -> ChangeRequest.create("CR-1", 1L, "P-1", 7L, "说明", CREATOR, T0, m))
        .hasMessageContaining("分段缺少具名 Owner");
  }

  @Test
  @DisplayName("未知分段码与非法版本 fail-closed")
  void invalidSegmentOrVersionShouldBeRejected() {
    assertThatThrownBy(() -> Segment.of("ACCOUNTING")).hasMessageContaining("未知审批分段");
    assertThatThrownBy(
            () -> ChangeRequest.create("CR-1", 1L, "P-1", 0L, "说明", CREATOR, T0, owners()))
        .hasMessageContaining("目标版本必须为正数");
  }

  @Test
  @DisplayName("仅创建人可提交，且只能提交一次")
  void submitGuard() {
    assertThatThrownBy(() -> draft().submit("999", T0)).hasMessageContaining("仅提交人本人");
    assertThat(inReview().getStatus()).isEqualTo(Status.IN_REVIEW);
    assertThatThrownBy(() -> inReview().submit(CREATOR, T0)).hasMessageContaining("仅 DRAFT 可提交");
  }

  @Test
  @DisplayName("N2：提交人自审必被拒绝")
  void selfApprovalShouldBeRejected() {
    assertThatThrownBy(
            () -> inReview().decide(Segment.GENERAL, ApprovalState.APPROVED, CREATOR, T0, null))
        .hasMessageContaining("SoD 违规：提交人不得自审");
  }

  @Test
  @DisplayName("非该段具名 Owner 越权审批被拒绝")
  void nonOwnerApprovalShouldBeRejected() {
    assertThatThrownBy(
            () -> inReview().decide(Segment.GENERAL, ApprovalState.APPROVED, OWNER_CC, T0, null))
        .hasMessageContaining("不是该分段具名 Owner");
  }

  @Test
  @DisplayName("同一段重复裁决被拒绝；驳回必须给原因")
  void duplicateDecisionAndRejectReason() {
    ChangeRequest first =
        inReview().decide(Segment.GENERAL, ApprovalState.APPROVED, OWNER_GENERAL, T0, null);
    assertThatThrownBy(
            () -> first.decide(Segment.GENERAL, ApprovalState.APPROVED, OWNER_GENERAL, T0, null))
        .hasMessageContaining("已裁决，禁止重复审批");
    assertThatThrownBy(
            () ->
                inReview().decide(Segment.GENERAL, ApprovalState.REJECTED, OWNER_GENERAL, T0, " "))
        .hasMessageContaining("驳回必须填写原因");
  }

  @Test
  @DisplayName("三段可任意顺序并行审批，全部通过才 APPROVED")
  void parallelApprovalOrderIndependent() {
    ChangeRequest two =
        inReview()
            .decide(Segment.PURCHASING_ORG, ApprovalState.APPROVED, OWNER_PO, T0, null)
            .decide(Segment.COMPANY_CODE, ApprovalState.APPROVED, OWNER_CC, T0, null);
    assertThat(two.getStatus()).isEqualTo(Status.IN_REVIEW);
    assertThat(two.allSegmentsApproved()).isFalse();

    ChangeRequest all = approved();
    assertThat(all.getStatus()).isEqualTo(Status.APPROVED);
    assertThat(all.allSegmentsApproved()).isTrue();
    assertThat(all.approvers()).containsExactlyInAnyOrder(OWNER_GENERAL, OWNER_CC, OWNER_PO);
    assertThat(all.getTrail()).hasSize(5);
  }

  @Test
  @DisplayName("任一段驳回 → 整笔 REJECTED，且不可应用")
  void rejectionBlocksApply() {
    ChangeRequest rejected =
        inReview().decide(Segment.GENERAL, ApprovalState.REJECTED, OWNER_GENERAL, T0, "税号与营业执照不一致");
    assertThat(rejected.getStatus()).isEqualTo(Status.REJECTED);
    assertThatThrownBy(() -> rejected.apply(APPLIER, T0)).hasMessageContaining("未全部审批通过");
  }

  @Test
  @DisplayName("N1：未全部审批直接覆盖 Active 区必被拒绝")
  void applyBeforeAllApprovedShouldBeRejected() {
    assertThatThrownBy(() -> inReview().apply(APPLIER, T0))
        .hasMessageContaining("未全部审批通过，禁止覆盖 Active 区");
  }

  @Test
  @DisplayName("双人复核：三段由同一人审批时不得应用")
  void singleApproverShouldBeRejected() {
    Map<Segment, String> m = owners();
    m.put(Segment.GENERAL, OWNER_GENERAL);
    m.put(Segment.COMPANY_CODE, OWNER_GENERAL);
    m.put(Segment.PURCHASING_ORG, OWNER_GENERAL);
    ChangeRequest cr =
        ChangeRequest.create("CR-2", 1L, "P-1", 7L, "说明", CREATOR, T0, m).submit(CREATOR, T0);
    for (Segment s : Segment.values()) {
      cr = cr.decide(s, ApprovalState.APPROVED, OWNER_GENERAL, T0, null);
    }
    assertThat(cr.getStatus()).isEqualTo(Status.APPROVED);
    ChangeRequest finalCr = cr;
    assertThatThrownBy(() -> finalCr.apply(APPLIER, T0)).hasMessageContaining("双人复核未满足");
  }

  @Test
  @DisplayName("三权分立：提交人或任一审批人不得作为应用人")
  void separationOfDutiesOnApply() {
    ChangeRequest cr = approved();
    assertThatThrownBy(() -> cr.apply(CREATOR, T0)).hasMessageContaining("SoD 违规");
    assertThatThrownBy(() -> cr.apply(OWNER_GENERAL, T0)).hasMessageContaining("SoD 违规");
  }

  @Test
  @DisplayName("合法应用 → APPLIED 且只可应用一次；已应用不可撤销")
  void applyOnce() {
    ChangeRequest applied = approved().apply(APPLIER, T0.plusSeconds(9));
    assertThat(applied.getStatus()).isEqualTo(Status.APPLIED);
    assertThat(applied.getAppliedBy()).isEqualTo(APPLIER);
    assertThat(applied.getTrail()).hasSize(6);
    assertThatThrownBy(() -> applied.apply(APPLIER, T0)).hasMessageContaining("禁止重复应用");
    assertThatThrownBy(() -> applied.cancel(CREATOR, T0)).hasMessageContaining("已应用");
  }

  @Test
  @DisplayName("不可变：状态迁移不改旧实例")
  void immutability() {
    ChangeRequest draft = draft();
    ChangeRequest next = draft.submit(CREATOR, T0);
    assertThat(draft.getStatus()).isEqualTo(Status.DRAFT);
    assertThat(next.getStatus()).isEqualTo(Status.IN_REVIEW);
    assertThat(draft.getTrail()).hasSize(1);
    assertThat(next.getTrail()).hasSize(2);
  }
}
