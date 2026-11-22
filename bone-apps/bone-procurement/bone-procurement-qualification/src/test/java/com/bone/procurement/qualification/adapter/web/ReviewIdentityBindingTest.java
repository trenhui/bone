package com.bone.procurement.qualification.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.procurement.qualification.adapter.web.controller.QualificationController;
import com.bone.procurement.qualification.adapter.web.controller.QualificationController.OcrReviewRequest;
import com.bone.procurement.qualification.application.OnboardingApplicationService;
import com.bone.procurement.qualification.application.QualificationApplicationService;
import com.bone.procurement.qualification.domain.gateway.ReviewIdentityPort;
import com.bone.procurement.qualification.domain.gateway.ReviewIdentityPort.ReviewIdentity;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 人工终决身份绑定：租户/复核人取登录态，请求体自报不一致即拒绝，不接受匿名/缺省租户。 */
class ReviewIdentityBindingTest {

  private final QualificationApplicationService service =
      mock(QualificationApplicationService.class);
  private final OnboardingApplicationService onboarding = mock(OnboardingApplicationService.class);
  private final ReviewIdentityPort identityPort = mock(ReviewIdentityPort.class);
  private final QualificationController controller =
      new QualificationController(service, onboarding, identityPort);

  @Test
  void 无登录态时拒绝且不落库() {
    when(identityPort.current()).thenReturn(null);

    assertThatThrownBy(() -> controller.reviewOcr(7L, new OcrReviewRequest(1L, "alice", true)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 请求租户与登录态不一致时拒绝() {
    when(identityPort.current()).thenReturn(new ReviewIdentity(1L, "alice"));

    assertThatThrownBy(() -> controller.reviewOcr(7L, new OcrReviewRequest(2L, "alice", true)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 请求复核人与登录态不一致时拒绝() {
    when(identityPort.current()).thenReturn(new ReviewIdentity(1L, "alice"));

    assertThatThrownBy(() -> controller.reviewOcr(7L, new OcrReviewRequest(1L, "bob", true)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 一致时按登录态权威值落库() {
    when(identityPort.current()).thenReturn(new ReviewIdentity(1L, "alice"));

    controller.reviewOcr(7L, new OcrReviewRequest(1L, "alice", true));

    ArgumentCaptor<Long> tenant = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> reviewer = ArgumentCaptor.forClass(String.class);
    verify(service)
        .reviewOcrByHuman(tenant.capture(), eq(7L), reviewer.capture(), eq(true), any(Date.class));
    assertThat(tenant.getValue()).isEqualTo(1L);
    assertThat(reviewer.getValue()).isEqualTo("alice");
  }
}
