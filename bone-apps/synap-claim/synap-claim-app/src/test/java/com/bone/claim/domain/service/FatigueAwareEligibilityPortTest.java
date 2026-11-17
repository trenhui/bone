package com.bone.claim.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.task.AssigneeType;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FatigueAwareEligibilityPortTest {

  private static ReviewFatigueConfig max(int n) {
    return new ReviewFatigueConfig(n, 120, 30, 10000L, Map.of());
  }

  @Test
  @DisplayName("边界：完成数等于上限即候选不可用")
  void atLimitIsIneligible() {
    AssigneeEligibilityPort delegate = mock(AssigneeEligibilityPort.class);
    when(delegate.isEligible(AssigneeType.USER, "u-1")).thenReturn(true);
    var port = new FatigueAwareEligibilityPort(delegate, max(2), id -> 2L);
    assertThat(port.isEligible(AssigneeType.USER, "u-1")).isFalse();
  }

  @Test
  @DisplayName("未超量保持原判定")
  void belowLimitRemainsEligible() {
    AssigneeEligibilityPort delegate = mock(AssigneeEligibilityPort.class);
    when(delegate.isEligible(AssigneeType.USER, "u-1")).thenReturn(true);
    var port = new FatigueAwareEligibilityPort(delegate, max(2), id -> 1L);
    assertThat(port.isEligible(AssigneeType.USER, "u-1")).isTrue();
  }

  @Test
  @DisplayName("原端口判不可用时不查完成量")
  void delegateRejectsShortCircuits() {
    AssigneeEligibilityPort delegate = mock(AssigneeEligibilityPort.class);
    var port =
        new FatigueAwareEligibilityPort(
            delegate,
            max(2),
            id -> {
              throw new AssertionError("不应查询");
            });
    assertThat(port.isEligible(AssigneeType.USER, "bad")).isFalse();
  }

  @Test
  @DisplayName("非 USER 候选不套用单班上限")
  void nonUserUnaffected() {
    AssigneeEligibilityPort delegate = mock(AssigneeEligibilityPort.class);
    when(delegate.isEligible(AssigneeType.DEPT, "d-1")).thenReturn(true);
    var port =
        new FatigueAwareEligibilityPort(
            delegate,
            max(1),
            id -> {
              throw new AssertionError("不应查询");
            });
    assertThat(port.isEligible(AssigneeType.DEPT, "d-1")).isTrue();
  }

  @Test
  @DisplayName("异常 fail-closed：不外溢，候选不可用")
  void countFailureIsIneligible() {
    AssigneeEligibilityPort delegate = mock(AssigneeEligibilityPort.class);
    when(delegate.isEligible(AssigneeType.USER, "u-2")).thenReturn(true);
    var port =
        new FatigueAwareEligibilityPort(
            delegate,
            max(2),
            id -> {
              throw new IllegalStateException("仓储不可用");
            });
    assertThat(port.isEligible(AssigneeType.USER, "u-2")).isFalse();
  }

  @Test
  @DisplayName("不可信计数（null / 负数）同样不可用")
  void untrustedCountIsIneligible() {
    AssigneeEligibilityPort delegate = mock(AssigneeEligibilityPort.class);
    when(delegate.isEligible(AssigneeType.USER, "u-3")).thenReturn(true);
    assertThat(
            new FatigueAwareEligibilityPort(delegate, max(2), id -> null)
                .isEligible(AssigneeType.USER, "u-3"))
        .isFalse();
    assertThat(
            new FatigueAwareEligibilityPort(delegate, max(2), id -> -1L)
                .isEligible(AssigneeType.USER, "u-3"))
        .isFalse();
  }
}
