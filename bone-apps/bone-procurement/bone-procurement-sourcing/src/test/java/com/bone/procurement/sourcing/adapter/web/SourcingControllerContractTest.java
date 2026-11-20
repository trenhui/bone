package com.bone.procurement.sourcing.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.sourcing.adapter.web.controller.SourcingController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

/** HTTP 端点契约：每个 POST 都带 @PreAuthorize 具名权限码。 */
class SourcingControllerContractTest {

  @Test
  @DisplayName("所有写端点都声明权限码")
  void allWriteEndpointsDeclareAuthority() {
    for (Method m : SourcingController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(PostMapping.class)) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertThat(pa).as("端点缺 @PreAuthorize: " + m.getName()).isNotNull();
        assertThat(pa.value()).contains("hasAuthority('procurement:");
      }
    }
  }

  @Test
  @DisplayName("端点数量与 OpenAPI §02 卷骨架对齐")
  void endpointCountMatchesContract() {
    long postCount =
        Arrays.stream(SourcingController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(PostMapping.class))
            .count();
    assertThat(postCount).isEqualTo(2L);
  }
}
