package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ReviewFatigueGuardService;
import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.review.ReviewFatigueDecision;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ReviewFatigueGuardControllerTest {

  private ReviewFatigueGuardService service;

  @BeforeEach
  void setUp() {
    MDC.put("userId", "u-1");
    service = mock(ReviewFatigueGuardService.class);
  }

  @AfterEach
  void tearDown() {
    MDC.remove("userId");
  }

  @Test
  @DisplayName("只读 GET 端点挂在已登记的白名单前缀，并复用只读权限点")
  void endpointContract() throws NoSuchMethodException {
    assertThat(ReviewFatigueGuardController.class.getAnnotation(RequestMapping.class).value())
        .contains("/api/v1/review/fatigue-guard");
    Method method =
        ReviewFatigueGuardController.class.getMethod(
            "evaluation", Integer.class, Integer.class, BigDecimal.class);
    assertThat(method.getAnnotation(GetMapping.class).value()).contains("/evaluation");
    PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
    assertThat(auth).isNotNull();
    assertThat(auth.value()).contains("claim:claims:read").doesNotContain("write");
  }

  @Test
  @DisplayName("操作人取验签身份，端点只委托一次只读评估")
  void delegatesOnceWithAuthenticatedOperator() {
    ReviewFatigueDecision decision =
        new ReviewFatigueDecision(false, true, false, ReviewFatigueConfig.defaults());
    when(service.evaluate(any(), any(), any(), any())).thenReturn(decision);

    var body =
        new ReviewFatigueGuardController(service).evaluation(120, 10, new BigDecimal("20000"));

    assertThat(body.getData()).isEqualTo(decision);
    verify(service, times(1)).evaluate("u-1", 120, 10, new BigDecimal("20000"));
  }
}
