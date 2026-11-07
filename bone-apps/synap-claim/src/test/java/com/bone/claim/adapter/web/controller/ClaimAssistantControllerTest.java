package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimAssistantApplicationService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 助手端点（NL 问答 + 受控工具面）契约测试：锁死「路由前缀」「权限点」「只读无副作用」三条边界。
 *
 * <p><b>为什么必须锁路由前缀</b>：网关按前缀白名单放行，{@code @RequestMapping} 漂成未登记前缀会表现为整体 404
 * 且后端无任何日志——「配了不生效」的典型形态，靠人工联调极难定位。故前缀与子路径都钉死，路径一改就转红。
 *
 * <p><b>为什么锁权限点</b>：新造权限码若不落 IAM 表，端点会对所有人 403；故三条端点都必须复用既有只读权限点 {@code
 * claim:claims:read}，不得误挂写权限（助手不参与金额/资金判定）。
 */
class ClaimAssistantControllerTest {

  @Test
  @DisplayName("路由前缀必须是被网关白名单覆盖的 /api/v1/claim-ops")
  void locksGatewayWhitelistedPrefix() {
    RequestMapping classMapping =
        ClaimAssistantController.class.getAnnotation(RequestMapping.class);

    assertThat(classMapping).isNotNull();
    assertThat(classMapping.value()).containsExactly("/api/v1/claim-ops");
  }

  @Test
  @DisplayName("ask 端点：POST /claim-assistant，复用 claim:claims:read，不挂写权限")
  void askEndpointContract() throws NoSuchMethodException {
    Method method =
        ClaimAssistantController.class.getMethod(
            "ask", ClaimAssistantController.AssistantRequest.class);

    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    assertThat(postMapping).isNotNull();
    assertThat(postMapping.value()).containsExactly("/claim-assistant");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains("claim:claims:read").doesNotContain("write");
  }

  @Test
  @DisplayName("tools 端点：GET /claim-assistant/tools，复用 claim:claims:read")
  void toolsEndpointContract() throws NoSuchMethodException {
    Method method = ClaimAssistantController.class.getMethod("tools");

    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    assertThat(getMapping).isNotNull();
    assertThat(getMapping.value()).containsExactly("/claim-assistant/tools");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains("claim:claims:read").doesNotContain("write");
  }

  @Test
  @DisplayName("runTool 端点：POST /claim-assistant/tool，复用 claim:claims:read")
  void runToolEndpointContract() throws NoSuchMethodException {
    Method method =
        ClaimAssistantController.class.getMethod(
            "runTool", ClaimAssistantController.ToolRunRequest.class);

    PostMapping postMapping = method.getAnnotation(PostMapping.class);
    assertThat(postMapping).isNotNull();
    assertThat(postMapping.value()).containsExactly("/claim-assistant/tool");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains("claim:claims:read").doesNotContain("write");
  }

  @Test
  @DisplayName("工具清单端点只调用服务一次，不顺手触发采样或其他副作用")
  void toolsDelegateHasNoSideEffect() {
    ClaimAssistantApplicationService service = mock(ClaimAssistantApplicationService.class);
    when(service.listTools()).thenReturn(List.of());

    new ClaimAssistantController(service).tools();

    verify(service, times(1)).listTools();
    verifyNoMoreInteractions(service);
  }
}
