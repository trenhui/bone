package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.bone.claim.application.AiEvalSampleIndexApplicationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * DIFF 样本索引端点（只读）的契约测试：锁住「路由前缀」「权限点」「只读无副作用」三条边界。
 *
 * <p><b>为什么必须锁路由前缀</b>：网关按前缀白名单放行，若本类 {@code @RequestMapping} 漂成未登记前缀， 表现为整体 404
 * 且<b>后端无任何日志</b>——「配了不生效」的典型形态，靠人工联调极难定位。 故这里把前缀与子路径都钉死，路径一改就转红。
 *
 * <p><b>为什么锁权限点</b>：新造权限码若不落 IAM 表，端点会对所有人 403（菜单可见、请求全拒）， 本仓已发生过；故断言复用既有只读权限点 {@code
 * claim:claims:read}。
 */
class AiEvalSampleIndexControllerTest {

  @Test
  @DisplayName("路由前缀必须是被网关白名单覆盖的 /api/v1/claim-ops，子路径为 /ai-eval-sample-index")
  void locksGatewayWhitelistedPrefixAndPath() throws NoSuchMethodException {
    RequestMapping classMapping =
        AiEvalSampleIndexController.class.getAnnotation(RequestMapping.class);
    assertThat(classMapping).isNotNull();
    assertThat(classMapping.value()).containsExactly("/api/v1/claim-ops");

    Method method = AiEvalSampleIndexController.class.getMethod("index");
    GetMapping getMapping = method.getAnnotation(GetMapping.class);
    assertThat(getMapping).isNotNull();
    assertThat(getMapping.value()).containsExactly("/ai-eval-sample-index");
  }

  @Test
  @DisplayName("权限点必须复用既有 claim:claims:read，不得新增权限码或误挂写权限")
  void usesExistingReadAuthority() throws NoSuchMethodException {
    Method method = AiEvalSampleIndexController.class.getMethod("index");
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("claim:claims:read");
    assertThat(annotation.value()).doesNotContain("write");
  }

  @Test
  @DisplayName("只读端点只调用 report 一次，不产生任何额外交互（不得顺手触发采样或清理）")
  void readOnlyDelegateHasNoSideEffect() {
    AiEvalSampleIndexApplicationService service = mock(AiEvalSampleIndexApplicationService.class);

    new AiEvalSampleIndexController(service).index();

    verify(service, times(1)).report();
    verifyNoMoreInteractions(service);
  }
}
