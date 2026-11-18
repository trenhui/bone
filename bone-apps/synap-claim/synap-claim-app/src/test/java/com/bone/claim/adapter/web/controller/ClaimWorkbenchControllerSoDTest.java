package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bone.claim.application.AiConfigGovernanceService;
import com.bone.claim.application.ClaimFeedbackApplicationService;
import com.bone.claim.application.ClaimSearchApplicationService;
import com.bone.claim.application.ClaimTaskApplicationService;
import com.bone.claim.application.IndependentReReviewApplicationService;
import com.bone.claim.application.port.out.TenantPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * 独立复审端点的职责分离（SoD）契约测试。
 *
 * <p>审核人/复审人必须取已验签操作人（{@code CurrentOperator}），不得采信请求体/查询参数里客户端自报的 reviewerId——否则「复核人 ≠
 * 第一审核人」校验退化为自我豁免。范式见 {@code ReviewTaskController}。
 */
class ClaimWorkbenchControllerSoDTest {

  private static final long CLAIM_ID = 777912345678901234L;
  private static final String AUTHENTICATED_OP = "op-from-token";
  private static final String SPOOFED_REVIEWER = "client-spoofed-reviewer";

  private IndependentReReviewApplicationService reReviewService;
  private ClaimWorkbenchController controller;

  @BeforeEach
  void setUp() {
    MDC.put("userId", AUTHENTICATED_OP);
    reReviewService = mock(IndependentReReviewApplicationService.class);
    controller =
        new ClaimWorkbenchController(
            mock(ClaimSearchApplicationService.class),
            mock(ClaimFeedbackApplicationService.class),
            mock(ClaimTaskApplicationService.class),
            mock(AiConfigGovernanceService.class),
            mock(TenantPort.class),
            reReviewService);
  }

  @AfterEach
  void tearDown() {
    MDC.remove("userId");
  }

  @Test
  @DisplayName("SoD：submit 复审人取验签操作人，忽略请求体自报的 reviewerId")
  void submitUsesAuthenticatedOperatorNotRequestBody() {
    controller.submitReReview(
        String.valueOf(CLAIM_ID), new ClaimWorkbenchController.ReReviewSubmitReq(SPOOFED_REVIEWER));

    verify(reReviewService).submitForReReview(CLAIM_ID, AUTHENTICATED_OP);
  }

  @Test
  @DisplayName("SoD：complete 结论人取验签操作人，忽略请求体自报的 reviewerId")
  void completeUsesAuthenticatedOperatorNotRequestBody() {
    controller.completeReReview(
        String.valueOf(CLAIM_ID),
        new ClaimWorkbenchController.ReReviewCompleteReq(true, SPOOFED_REVIEWER));

    verify(reReviewService).completeReReview(CLAIM_ID, true, AUTHENTICATED_OP);
  }

  @Test
  @DisplayName("SoD：queue 持权人视角——忽略客户端 reviewerId 查询参数，一律取验签操作人")
  void queueUsesAuthenticatedOperatorNotQueryParam() {
    controller.reReviewQueue(SPOOFED_REVIEWER, 1, 20);

    verify(reReviewService).queue(AUTHENTICATED_OP, 1, 20);
  }

  @Test
  @DisplayName("SoD：当前请求体 DTO 仍保留 reviewerId 字段但服务端不采信（API 兼容）")
  void requestDtoStillAcceptsReviewerIdField() {
    // 契约兼容：字段仍在，客户端传值不报错，但上面三个用例证明服务端不采信。
    assertThat(new ClaimWorkbenchController.ReReviewSubmitReq(SPOOFED_REVIEWER).reviewerId())
        .isEqualTo(SPOOFED_REVIEWER);
  }
}
