package com.bone.procurement.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

/** 就绪度端点契约：具名权限码、只读 GET、不随治理开关关闭（无 @ConditionalOnAiGovernanceEnabled）。 */
class AiReadinessControllerContractTest {

  @Test
  void 端点声明具名权限码() {
    Method readiness;
    try {
      readiness = AiReadinessController.class.getMethod("readiness");
    } catch (NoSuchMethodException e) {
      throw new AssertionError("缺少 readiness 端点", e);
    }
    assertThat(readiness.isAnnotationPresent(GetMapping.class)).as("必须是只读 GET").isTrue();
    PreAuthorize pa = readiness.getAnnotation(PreAuthorize.class);
    assertThat(pa).as("端点缺权限码").isNotNull();
    assertThat(pa.value()).contains("procurement:ai-readiness:read");
  }

  @Test
  void 端点始终注册不随治理开关关闭() {
    assertThat(AiReadinessController.class.getAnnotations())
        .as("就绪度端点必须始终可用，不得随治理条件关闭")
        .noneMatch(
            a -> a.annotationType().getSimpleName().contains("ConditionalOnAiGovernanceEnabled"));
  }
}
