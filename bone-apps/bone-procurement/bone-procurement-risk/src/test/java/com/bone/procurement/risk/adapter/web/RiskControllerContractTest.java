package com.bone.procurement.risk.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.risk.adapter.web.controller.RiskController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** 端点契约：所有 HTTP 方法都带具名权限码。 */
class RiskControllerContractTest {

  @Test
  @DisplayName("所有端点都声明权限码")
  void allEndpointsDeclareAuthority() {
    for (Method m : RiskController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(GetMapping.class)) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertThat(pa).as("端点缺 @PreAuthorize: " + m.getName()).isNotNull();
        assertThat(pa.value()).contains("hasAuthority('procurement:");
      }
    }
  }

  @Test
  @DisplayName("端点数量与 OpenAPI §03 卷骨架对齐")
  void endpointCountMatchesContract() {
    long writeCount =
        Arrays.stream(RiskController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(PostMapping.class))
            .count();
    long readCount =
        Arrays.stream(RiskController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(GetMapping.class))
            .count();
    assertThat(writeCount).isEqualTo(1L);
    assertThat(readCount).isEqualTo(1L);
  }
}
