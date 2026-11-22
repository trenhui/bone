package com.bone.claim.adapter.web.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq;
import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq.Rejection;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 契约决策请求组装测试：拒赔五要素拼装 + 受控原因码硬约束（R-MAN-03 / §8.3）。 */
class ReviewDecisionAssemblerTest {

  @Test
  @DisplayName("拒赔五要素按固定顺序拼装（空格被裁剪，空项跳过）")
  void composesFiveElements() {
    ReviewDecisionReq request =
        req(
            Decision.REJECTED,
            null,
            new Rejection(
                " 门诊诊查费不属于保单责任 ",
                "条款 3.2 责任免除第 4 项",
                List.of("doc-001", "doc-002"),
                "appeals@insurer.example / 400-xxx",
                "2026-04-12"));

    assertThat(ReviewDecisionAssembler.composeComment(request))
        .isEqualTo(
            "事实认定：门诊诊查费不属于保单责任"
                + "；条款依据：条款 3.2 责任免除第 4 项"
                + "；证据位置：doc-001,doc-002"
                + "；申诉渠道：appeals@insurer.example / 400-xxx"
                + "；法定时限：2026-04-12");
  }

  @Test
  @DisplayName("五要素全空时回退 comment（不拼出空串掩盖缺失）")
  void fallsBackToComment() {
    ReviewDecisionReq request =
        req(Decision.REJECTED, "人工填写的拒赔说明", new Rejection(null, " ", List.of(), null, null));
    assertThat(ReviewDecisionAssembler.composeComment(request)).isEqualTo("人工填写的拒赔说明");

    ReviewDecisionReq noRejection = req(Decision.REJECTED, "只有意见", null);
    assertThat(ReviewDecisionAssembler.composeComment(noRejection)).isEqualTo("只有意见");
  }

  @Test
  @DisplayName("非拒赔不改写意见（APPROVED 的 comment 原样透传）")
  void passesThroughForApproved() {
    ReviewDecisionReq request = req(Decision.APPROVED, "发票与清单一致", null);
    assertThat(ReviewDecisionAssembler.composeComment(request)).isEqualTo("发票与清单一致");
    assertThat(ReviewDecisionAssembler.requireRejectType(request)).isNull();
  }

  @Test
  @DisplayName("拒赔缺受控原因码 → 400（不允许自由文本兜底）")
  void requiresRejectTypeForRejection() {
    BizException ex =
        catchThrowableOfType(
            () -> ReviewDecisionAssembler.requireRejectType(req(Decision.REJECTED, "意见", null)),
            BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.CLAIM_REJECT_REASON_REQUIRED);
    assertThat(ex.getCode()).isEqualTo(400);
  }

  @Test
  @DisplayName("拒赔带原因码 → 裁剪后透传")
  void trimsRejectType() {
    ReviewDecisionReq request =
        new ReviewDecisionReq(
            Decision.REJECTED,
            "u-1001",
            "u-1002",
            "意见",
            List.of("doc-001"),
            new Rejection("事实", "条款", List.of("doc-001"), "渠道", "时限"),
            "  03 ",
            null);
    assertThat(ReviewDecisionAssembler.requireRejectType(request)).isEqualTo("03");
  }

  @Test
  @DisplayName("拒赔证据位置：EV 编号与 #p 页码锚点均放行（含前后空格裁剪）")
  void acceptsValidEvidenceRefs() {
    ReviewDecisionReq request =
        req(
            Decision.REJECTED,
            null,
            new Rejection("事实", "条款", List.of(" EV-001 ", "EV-0002#p3"), "渠道", "时限"));
    ReviewDecisionAssembler.requireEvidenceRefs(request);
  }

  @Test
  @DisplayName("非拒赔跳过证据位置校验（没有 rejection 也不报错）")
  void skipsEvidenceRefCheckForApproved() {
    ReviewDecisionAssembler.requireEvidenceRefs(req(Decision.APPROVED, "意见", null));
  }

  @Test
  @DisplayName("拒赔证据位置缺失 → 400（必填，不留空混进合规文书）")
  void rejectsMissingEvidenceRefs() {
    assertInvalid(req(Decision.REJECTED, "意见", new Rejection("事实", "条款", List.of(), "渠道", "时限")));
    assertInvalid(req(Decision.REJECTED, "意见", new Rejection("事实", "条款", null, "渠道", "时限")));
    assertInvalid(req(Decision.REJECTED, "意见", null));
  }

  @Test
  @DisplayName("拒赔证据位置为自由文本/doc 编号/非法页码 → 400（可定位性硬约束）")
  void rejectsNonLocatableRefs() {
    for (String bad :
        List.of(
            "doc-001",
            "发票第一页",
            "EV-1",
            "EV-001A",
            "EV-001#p0",
            "EV-001#px",
            "../../secret",
            "EV-001; DROP TABLE")) {
      assertInvalid(
          req(Decision.REJECTED, "意见", new Rejection("事实", "条款", List.of(bad), "渠道", "时限")));
    }
  }

  @Test
  @DisplayName("拒赔证据位置超量 / 单项超长 → 400")
  void rejectsOversizedRefs() {
    List<String> tooMany =
        java.util.stream.IntStream.rangeClosed(1, 21)
            .mapToObj(i -> String.format("EV-%04d", i))
            .toList();
    assertInvalid(req(Decision.REJECTED, "意见", new Rejection("事实", "条款", tooMany, "渠道", "时限")));
    assertInvalid(
        req(
            Decision.REJECTED,
            "意见",
            new Rejection("事实", "条款", List.of("EV-001" + "x".repeat(40)), "渠道", "时限")));
  }

  private static void assertInvalid(ReviewDecisionReq request) {
    BizException ex =
        catchThrowableOfType(
            () -> ReviewDecisionAssembler.requireEvidenceRefs(request), BizException.class);
    assertThat(ex).as("应拒绝非法证据位置").isNotNull();
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.SC_CLAIM_REJECT_EVIDENCE_REF_INVALID);
    assertThat(ex.getCode()).isEqualTo(400);
  }

  private static ReviewDecisionReq req(Decision decision, String comment, Rejection rejection) {
    return new ReviewDecisionReq(
        decision, "u-1001", "u-1002", comment, List.of("doc-001"), rejection, null, null);
  }
}
