package com.bone.procurement.governance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.governance.adapter.web.controller.SafetyController;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 安全治理端点契约：路径 / 权限码 / ApiResponse 包装 / 能力开关。 */
class SafetyControllerContractTest {

  @Test
  void 根路径与开关条件() {
    assertThat(SafetyController.class.getAnnotation(RequestMapping.class).value()[0])
        .isEqualTo("/api/v1/procurement/ai-governance/safety");
    assertThat(SafetyController.class.isAnnotationPresent(ConditionalOnAiGovernanceEnabled.class))
        .isTrue();
  }

  @Test
  void 六端点路径与方法() {
    assertThat(hasGet("/kill-switch")).isTrue();
    assertThat(hasPost("/kill-switch/engage")).isTrue();
    assertThat(hasPost("/kill-switch/disengage")).isTrue();
    assertThat(hasGet("/kill-switch/drill-status")).isTrue();
    assertThat(hasPost("/content-labels/check")).isTrue();
    assertThat(hasPost("/fairness/evaluate")).isTrue();
  }

  @Test
  void 写读权限码分离() {
    assertAuthority("engage", "procurement:ai-governance:write");
    assertAuthority("disengage", "procurement:ai-governance:write");
    assertAuthority("currentKillSwitch", "procurement:ai-governance:read");
    assertAuthority("drillStatus", "procurement:ai-governance:read");
    assertAuthority("checkDelivery", "procurement:ai-governance:read");
    assertAuthority("evaluateFairness", "procurement:ai-governance:read");
  }

  @Test
  void 所有端点返回ApiResponse包装() {
    for (Method m : SafetyController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(GetMapping.class)) {
        assertThat(m.getReturnType()).as(m.getName()).isEqualTo(ApiResponse.class);
      }
    }
  }

  private static boolean hasGet(String path) {
    for (Method m : SafetyController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(GetMapping.class)) {
        String[] v = m.getAnnotation(GetMapping.class).value();
        if (v.length > 0 && v[0].equals(path)) return true;
      }
    }
    return false;
  }

  private static boolean hasPost(String path) {
    for (Method m : SafetyController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(PostMapping.class)) {
        String[] v = m.getAnnotation(PostMapping.class).value();
        if (v.length > 0 && v[0].equals(path)) return true;
      }
    }
    return false;
  }

  private static void assertAuthority(String name, String authority) {
    for (Method m : SafetyController.class.getDeclaredMethods()) {
      if (m.getName().equals(name)) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertThat(pa).as(name).isNotNull();
        assertThat(pa.value()).contains(authority);
        return;
      }
    }
    throw new AssertionError("method not found: " + name);
  }
}
