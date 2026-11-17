package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.review.ReviewFatigueDecision;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewFatigueGuardServiceTest {

  private static final long TENANT = 9L;
  private static final ReviewFatigueConfig CFG =
      new ReviewFatigueConfig(2, 120, 30, 10000L, Map.of());

  private FeatureTenantToggleService toggleService;
  private ClaimTaskRepository repository;
  private TenantPort tenantPort;
  private ReviewFatigueGuardService service;

  @BeforeEach
  void setUp() {
    toggleService = mock(FeatureTenantToggleService.class);
    repository = mock(ClaimTaskRepository.class);
    tenantPort = mock(TenantPort.class);
    service = new ReviewFatigueGuardService(toggleService, repository, tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(toggleService.reviewFatigueConfig(TENANT)).thenReturn(CFG);
  }

  @Test
  @DisplayName("超量边界：完成数等于 maxPerShift 即 overloaded")
  void overloadBoundary() {
    when(repository.countFinishedTodayByAssignee(anyLong(), any(), any(Instant.class)))
        .thenReturn(2L);
    assertThat(service.evaluate("u-1", null, null, null).overloaded()).isTrue();
  }

  @Test
  @DisplayName("未超量：完成数小于上限保持 false")
  void belowLimit() {
    when(repository.countFinishedTodayByAssignee(anyLong(), any(), any(Instant.class)))
        .thenReturn(1L);
    assertThat(service.evaluate("u-1", null, null, null).overloaded()).isFalse();
  }

  @Test
  @DisplayName("休息提示边界：等于阈值即提示，空值不提示")
  void restPromptBoundary() {
    ReviewFatigueDecision at = service.evaluate(null, 120, null, null);
    ReviewFatigueDecision below = service.evaluate(null, 119, null, null);
    ReviewFatigueDecision missing = service.evaluate(null, null, null, null);
    assertThat(at.restPrompt()).isTrue();
    assertThat(below.restPrompt()).isFalse();
    assertThat(missing.restPrompt()).isFalse();
  }

  @Test
  @DisplayName("敏感案件：金额达阈值且班末不足阈值 ⇒ sensitiveEligible=false（只提示）")
  void sensitiveShiftEndBoundary() {
    assertThat(service.evaluate(null, null, 29, new BigDecimal("10000")).sensitiveEligible())
        .isFalse();
    assertThat(service.evaluate(null, null, 30, new BigDecimal("10000")).sensitiveEligible())
        .isTrue();
    assertThat(service.evaluate(null, null, 1, new BigDecimal("9999.99")).sensitiveEligible())
        .isTrue();
  }

  @Test
  @DisplayName("取数异常：不外溢，评估按超量收敛")
  void countFailureConvergesToOverloaded() {
    when(repository.countFinishedTodayByAssignee(anyLong(), any(), any(Instant.class)))
        .thenThrow(new IllegalStateException("DB down"));
    assertThat(service.evaluate("u-1", null, null, null).overloaded()).isTrue();
  }

  @Test
  @DisplayName("配置拒绝项透传到评估结论，供工作台显示「配了不生效」")
  void rejectedExposed() {
    when(toggleService.reviewFatigueConfig(TENANT))
        .thenReturn(new ReviewFatigueConfig(2, 120, 30, 10000L, Map.of("maxPerShift", "越界")));
    assertThat(service.evaluate(null, null, null, null).rejected()).containsKey("maxPerShift");
  }

  @Test
  @DisplayName("无可评估人时不查仓储，绝不以查不到为可用事实")
  void blankAssigneeSkipsRepository() {
    service.evaluate("", 120, 0, new BigDecimal("20000"));
    verifyNoInteractions(repository);
  }
}
