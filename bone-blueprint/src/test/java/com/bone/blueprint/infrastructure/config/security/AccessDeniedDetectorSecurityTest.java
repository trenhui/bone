package com.bone.blueprint.infrastructure.config.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.AccessDeniedDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.ClassUtils;

/**
 * {@link AccessDeniedDetector} 在<b>有 Spring Security</b> 模块上的行为契约。
 *
 * <p>本测试存在的唯一理由是锁死一条容易被「优化」掉的性质：判定必须用 {@code isAssignableFrom} 覆盖 <b>子类</b>。 早期实现按类名精确 {@code
 * equals} 比对，鉴权异常一旦被子类化（如 {@code CsrfException} 或各业务自定义的子类）就会被漏判，重新掉回 500 兜底 ——
 * 且不会有任何报错提示，属于典型的静默回归。
 *
 * <p>side-box：{@code bone-web} 侧另有「无 security 时安全缺席」的测试，两侧合起来覆盖完整契约。
 */
class AccessDeniedDetectorSecurityTest {

  /** 自造子类：真实业务里鉴权异常会被多种场景派生成子类，故用自定义子类模拟。 */
  static class TenantAccessDeniedException extends AccessDeniedException {

    TenantAccessDeniedException(String message) {
      super(message);
    }
  }

  @Test
  @DisplayName("精确类型 AccessDeniedException 必须命中")
  void exactTypeIsDetected() {
    assertTrue(AccessDeniedDetector.isAccessDenied(new AccessDeniedException("denied")));
  }

  @Test
  @DisplayName("AccessDeniedException 的子类必须命中（类名精确比对会漏判）")
  void subclassIsDetected() {
    assertTrue(
        AccessDeniedDetector.isAccessDenied(new TenantAccessDeniedException("tenant denied")),
        "子类必须被识别为鉴权失败，否则 @PreAuthorize 失败会回落成 500");
  }

  @Test
  @DisplayName("被包装进 cause 链时仍须命中（只看顶层类型会漏判）")
  void wrappedInCauseChainIsDetected() {
    RuntimeException wrapper =
        new RuntimeException("service layer", new AccessDeniedException("denied"));
    assertTrue(AccessDeniedDetector.isAccessDenied(wrapper));
  }

  @Test
  @DisplayName("Security 6 新授权栈 AuthorizationDeniedException 必须命中（类存在时）")
  void authorizationDeniedExceptionIsDetected() {
    boolean present =
        ClassUtils.isPresent(
            "org.springframework.security.authorization.AuthorizationDeniedException", null);
    if (!present) {
      // 当前 Security 版本未提供该类型，属预期，直接放行（断言不适用而非失败）
      return;
    }
    try {
      Object ex =
          Class.forName("org.springframework.security.authorization.AuthorizationDeniedException")
              .getConstructor(String.class)
              .newInstance("denied");
      assertTrue(AccessDeniedDetector.isAccessDenied((Throwable) ex));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("类型存在但构造签名不符，请同步更新判定列表", e);
    }
  }

  @Test
  @DisplayName("非鉴权异常不得误判为 403")
  void unrelatedThrowableIsNotDetected() {
    assertFalse(AccessDeniedDetector.isAccessDenied(new IllegalStateException("boom")));
    assertFalse(
        AccessDeniedDetector.isAccessDenied(
            new RuntimeException("x", new IllegalArgumentException("y"))));
  }
}
