package com.bone.core.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.security.jwt.JwtPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link CurrentOperator} 单测：证明操作人身份<b>只</b>来自服务端已验签 claim。
 *
 * <p>这一层是职责分离（SoD）的地基——若身份能从请求头/请求体取值，双人复核就只是调用方的自我声明。 因此本测试刻意覆盖三件事：上下文优先、MDC 回退、无身份时响亮失败（不退化成
 * null）。
 */
class CurrentOperatorTest {

  private static JwtPrincipal principal(String userId) {
    return new JwtPrincipal(userId, "tester", "0", List.of("claim:review:write"), List.of());
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  @DisplayName("有认证上下文 → 取 token 内 userId")
  void readsFromSecurityContext() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal("u-42"), null, List.of()));

    assertThat(CurrentOperator.id()).contains("u-42");
    assertThat(CurrentOperator.requireId()).isEqualTo("u-42");
  }

  @Test
  @DisplayName("安全链已清空上下文 → 回退 MDC（同一份 claim 派生，可信度不变）")
  void fallsBackToMdc() {
    MDC.put("userId", "u-mdc");

    assertThat(CurrentOperator.requireId()).isEqualTo("u-mdc");
  }

  @Test
  @DisplayName("上下文优先于 MDC，二者不一致时以已验签 claim 为准")
  void contextWinsOverMdc() {
    MDC.put("userId", "u-stale");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal("u-real"), null, List.of()));

    assertThat(CurrentOperator.requireId()).isEqualTo("u-real");
  }

  @Test
  @DisplayName("无身份 → requireId 抛异常，绝不返回 null/空串（避免操作人字段静默变空）")
  void failsLoudlyWithoutIdentity() {
    assertThat(CurrentOperator.id()).isEmpty();
    assertThatThrownBy(CurrentOperator::requireId)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("无法确定当前操作人");
  }

  @Test
  @DisplayName("空白 userId 视同缺失")
  void blankUserIdTreatedAsAbsent() {
    MDC.put("userId", "   ");

    assertThat(CurrentOperator.id()).isEmpty();
  }
}
