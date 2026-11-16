package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.model.ApiResponse;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * 批 2 配置治理端点的契约钉子。
 *
 * <p>路径漂出网关白名单前缀会整体 404 且后端无日志；漏挂 {@code @PreAuthorize} 会让写端点对所有人开放或全 403。 这两类「配了不生效」必须在编译期之外再钉一次。
 */
class AiConfigGovernanceControllerTest {

  @Test
  @DisplayName("治理快照：GET /ai/config/governance 复用只读权限 claim:claims:read")
  void governanceSnapshotContract() throws NoSuchMethodException {
    Method method = ClaimWorkbenchController.class.getMethod("configGovernance");

    assertThat(method.getAnnotation(GetMapping.class).value())
        .containsExactly("/ai/config/governance");
    PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
    assertThat(auth).as("缺 @PreAuthorize").isNotNull();
    assertThat(auth.value()).isEqualTo("hasAuthority('claim:claims:read')");
    assertThat(method.getReturnType()).isEqualTo(ApiResponse.class);
  }

  @Test
  @DisplayName("受控写：PUT /ai/config/triage-limits 必须挂写权限 claim:review:write")
  void controlledWriteContract() throws NoSuchMethodException {
    Method method = ClaimWorkbenchController.class.getMethod("updateTriageLimits", Map.class);

    assertThat(method.getAnnotation(PutMapping.class).value())
        .containsExactly("/ai/config/triage-limits");
    PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
    assertThat(auth).as("写端点缺 @PreAuthorize").isNotNull();
    assertThat(auth.value()).isEqualTo("hasAuthority('claim:review:write')");
    assertThat(method.getReturnType()).isEqualTo(ApiResponse.class);
  }

  @Test
  @DisplayName("类级路径仍在网关已白名单的 /api/v1/claims 下")
  void staysOnWhitelistedPrefix() {
    assertThat(
            ClaimWorkbenchController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                .value())
        .containsExactly("/api/v1/claims");
  }
}
