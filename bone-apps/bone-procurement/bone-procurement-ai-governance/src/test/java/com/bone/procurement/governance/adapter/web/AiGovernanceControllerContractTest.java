package com.bone.procurement.governance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.governance.adapter.web.controller.AiGovernanceController;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTTP 契约：路径 / 方法 / 权限码 / ApiResponse 包装 / 能力开关条件。 */
class AiGovernanceControllerContractTest {

  @Test
  void 根路径与能力开关条件锁定() {
    assertThat(AiGovernanceController.class.getAnnotation(RequestMapping.class).value()[0])
        .isEqualTo("/api/v1/procurement/ai-governance");
    assertThat(
            AiGovernanceController.class.isAnnotationPresent(
                ConditionalOnAiGovernanceEnabled.class))
        .isTrue();
    ConditionalOnAiGovernanceEnabled cond =
        AiGovernanceController.class.getAnnotation(ConditionalOnAiGovernanceEnabled.class);
    ConditionalOnProperty property =
        ConditionalOnProperty.class.cast(
            ConditionalOnAiGovernanceEnabled.class.getAnnotation(ConditionalOnProperty.class));
    assertThat(cond).isNotNull();
    assertThat(property.prefix()).isEqualTo("bone.procurement.ai-governance");
    assertThat(property.havingValue()).isEqualTo("true");
  }

  @Test
  void 十端点路径与方法锁定() {
    assertThat(hasPost("/capabilities")).isTrue();
    assertThat(hasGet("/capabilities")).isTrue();
    assertThat(hasGet("/capabilities/{code}")).isTrue();
    assertThat(hasPost("/capabilities/{code}/state")).isTrue();
    assertThat(hasPost("/capabilities/{code}/decide")).isTrue();
    assertThat(hasGet("/capabilities/{code}/trace")).isTrue();
    assertThat(hasGet("/capabilities/{code}/overrides")).isTrue();
    assertThat(hasGet("/capabilities/{code}/drift")).isTrue();
    assertThat(hasGet("/capabilities/{code}/oversight")).isTrue();
    assertThat(hasPost("/human-decisions")).isTrue();
  }

  @Test
  void 写读权限码锁定() {
    assertAuthority("register", "procurement:ai-governance:write");
    assertAuthority("changeState", "procurement:ai-governance:write");
    assertAuthority("recordHumanDecision", "procurement:ai-governance:write");
    assertAuthority("listCapabilities", "procurement:ai-governance:read");
    assertAuthority("getCapability", "procurement:ai-governance:read");
    assertAuthority("decide", "procurement:ai-governance:read");
    assertAuthority("trace", "procurement:ai-governance:read");
    assertAuthority("countOverrides", "procurement:ai-governance:read");
    assertAuthority("drift", "procurement:ai-governance:read");
    assertAuthority("oversight", "procurement:ai-governance:read");
  }

  @Test
  void oversight端点只接受路径参数不接受阈值注入() throws NoSuchMethodException {
    Method m = AiGovernanceController.class.getDeclaredMethod("oversight", String.class);
    assertThat(m.getParameterTypes()).containsExactly(String.class);
    assertThat(m.getAnnotation(GetMapping.class).value()[0])
        .isEqualTo("/capabilities/{code}/oversight");
  }

  @Test
  void 所有端点返回ApiResponse包装() {
    for (Method m : AiGovernanceController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(GetMapping.class)) {
        assertThat(m.getReturnType())
            .as(m.getName() + " 必须用 ApiResponse<T> 包装")
            .isEqualTo(ApiResponse.class);
      }
    }
  }

  private static boolean hasGet(String path) {
    return hasMapping(path, GetMapping.class);
  }

  private static boolean hasPost(String path) {
    return hasMapping(path, PostMapping.class);
  }

  private static boolean hasMapping(
      String path, Class<? extends java.lang.annotation.Annotation> ann) {
    for (Method m : AiGovernanceController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(ann)) {
        String[] value =
            ann == GetMapping.class
                ? m.getAnnotation(GetMapping.class).value()
                : m.getAnnotation(PostMapping.class).value();
        if (value.length > 0 && value[0].equals(path)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void assertAuthority(String methodName, String authority) {
    try {
      Method m =
          AiGovernanceController.class.getDeclaredMethod(methodName, methodParamTypes(methodName));
      PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
      assertThat(pa).as(methodName + " 必须有 @PreAuthorize").isNotNull();
      assertThat(pa.value()).contains(authority);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  private static Class<?>[] methodParamTypes(String name) {
    return switch (name) {
      case "register" -> new Class<?>[] {
        com.bone.procurement.governance.adapter.web.controller.RegisterCapabilityRequest.class
      };
      case "changeState" -> new Class<?>[] {
        String.class,
        com.bone.procurement.governance.adapter.web.controller.CapabilityStateRequest.class
      };
      case "recordHumanDecision" -> new Class<?>[] {
        com.bone.procurement.governance.adapter.web.controller.HumanDecisionRequest.class
      };
      case "listCapabilities" -> new Class<?>[0];
      case "getCapability", "decide", "trace", "countOverrides", "drift", "oversight" -> new Class<
              ?>[] {String.class};
      default -> throw new IllegalArgumentException(name);
    };
  }
}
