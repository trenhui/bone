package com.bone.procurement.governance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.governance.adapter.web.controller.AiGovernanceController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

/** 治理态势聚合端点契约：只读 GET、read 权限、无客户端阈值参数、ApiResponse 包装。 */
class PostureEndpointContractTest {

  @Test
  void posture端点为只读GET且无客户端参数() throws NoSuchMethodException {
    Method m = AiGovernanceController.class.getDeclaredMethod("posture");
    assertThat(m.getParameterTypes()).isEmpty();
    assertThat(m.getAnnotation(GetMapping.class).value()).containsExactly("/posture");
    assertThat(m.getReturnType()).isEqualTo(ApiResponse.class);
  }

  @Test
  void posture端点需要read权限() throws NoSuchMethodException {
    Method m = AiGovernanceController.class.getDeclaredMethod("posture");
    PreAuthorize auth = m.getAnnotation(PreAuthorize.class);
    assertThat(auth).isNotNull();
    assertThat(auth.value()).contains("procurement:ai-governance:read");
    assertThat(auth.value()).doesNotContain("write");
  }
}
