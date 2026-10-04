package com.bone.core.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AccessDeniedDetector} 在<b>无 Spring Security</b> 模块上的行为契约。
 *
 * <p>本模块（bone-web）刻意不依赖 security，因此只能验证「安全缺席」这一侧：判定必须全量返回 false， 且不得因类型解析失败而抛出异常影响启动。 「有
 * security」那一侧（子类必须被识别）由装 security 的模块 测——见 blueprint 的 {@code AccessDeniedDetectorSecurityTest}。
 */
class AccessDeniedDetectorNoSecurityTest {

  @Test
  @DisplayName("null 与普通异常都不应判为鉴权失败")
  void nonSecurityThrowableIsNotAccessDenied() {
    assertFalse(AccessDeniedDetector.isAccessDenied(null));
    assertFalse(AccessDeniedDetector.isAccessDenied(new IllegalStateException("boom")));
    assertFalse(
        AccessDeniedDetector.isAccessDenied(
            new RuntimeException("outer", new IllegalArgumentException("inner"))));
  }

  @Test
  @DisplayName("深层 cause 链应能遍历且不误判（无 security 时全部候选类型为 null）")
  void deepCauseChainIsTraversedSafely() {
    Throwable deep =
        new RuntimeException(
            "l3", new IllegalStateException("l2", new IllegalArgumentException("l1")));
    assertFalse(AccessDeniedDetector.isAccessDenied(deep));
  }

  @Test
  @DisplayName("类可加载且判定入口可用（本模块无 security 时不应启动失败）")
  void detectorIsUsableWithoutSecurity() {
    assertNotNull(AccessDeniedDetector.class.getName());
    assertFalse(AccessDeniedDetector.isAccessDenied(new Exception("x")));
  }
}
