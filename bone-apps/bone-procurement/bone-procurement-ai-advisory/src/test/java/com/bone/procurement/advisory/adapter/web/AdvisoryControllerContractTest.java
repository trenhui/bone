package com.bone.procurement.advisory.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.advisory.adapter.web.controller.AdvisoryController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

/** 端点契约：所有端点带具名权限码。 */
class AdvisoryControllerContractTest {

  @Test
  @DisplayName("所有端点都声明权限码")
  void allEndpointsDeclareAuthority() {
    for (Method m : AdvisoryController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(GetMapping.class)) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertThat(pa).as("端点缺 @PreAuthorize: " + m.getName()).isNotNull();
        assertThat(pa.value()).contains("hasAuthority('procurement:ai-advisory:read')");
      }
    }
  }
}
