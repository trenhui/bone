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

  private static ReviewDecisionReq req(Decision decision, String comment, Rejection rejection) {
    return new ReviewDecisionReq(
        decision, "u-1001", "u-1002", comment, List.of("doc-001"), rejection, null, null);
  }
}
