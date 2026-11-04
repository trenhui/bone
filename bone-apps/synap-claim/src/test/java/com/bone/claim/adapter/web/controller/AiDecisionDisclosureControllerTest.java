package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiDecisionDisclosureService;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure;
import com.bone.claim.domain.model.decision.AiDecisionDisclosure.Disclosure;
import com.bone.core.model.ApiResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/** 端点契约护栏：复用既有权限、纯只读、参数不丢、不把服务结果改写成乐观结论。 */
class AiDecisionDisclosureControllerTest {

  private final AiDecisionDisclosureService service = mock(AiDecisionDisclosureService.class);
  private final AiDecisionDisclosureController controller =
      new AiDecisionDisclosureController(service);

  @Test
  @DisplayName("复用 claim:claims:read，不新增权限点")
  void reusesExistingReadAuthority() throws Exception {
    Method m = AiDecisionDisclosureController.class.getMethod("disclose", long.class, String.class);
    PreAuthorize annotation = m.getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("hasAuthority('claim:claims:read')");
  }

  @Test
  @DisplayName("负向探针：只暴露 GET，没有写方法注解（只读端点不得有副作用）")
  void onlyGetMapping() throws Exception {
    Method m = AiDecisionDisclosureController.class.getMethod("disclose", long.class, String.class);

    assertThat(m.getAnnotation(GetMapping.class)).isNotNull();
    assertThat(m.getAnnotation(PostMapping.class)).isNull();
    assertThat(m.getAnnotation(PutMapping.class)).isNull();
    assertThat(m.getAnnotation(DeleteMapping.class)).isNull();
  }

  @Test
  @DisplayName("不带 traceId 时按案件取最近一次执行，参数如实透传")
  void nullTraceIdIsPassedThrough() {
    Disclosure expected = AiDecisionDisclosure.undetermined(1001L, "NO_EXECUTION_RECORD");
    when(service.disclose(1001L, null)).thenReturn(expected);

    ApiResponse<Disclosure> response = controller.disclose(1001L, null);

    assertThat(response.getData()).isSameAs(expected);
    verify(service).disclose(1001L, null);
  }

  @Test
  @DisplayName("显式 traceId 原样透传，服务端负责跨案校验")
  void explicitTraceIdIsPassedThrough() {
    Disclosure expected = AiDecisionDisclosure.undetermined(1001L, "TRACE_CLAIM_MISMATCH");
    when(service.disclose(1001L, "trace-of-other")).thenReturn(expected);

    ApiResponse<Disclosure> response = controller.disclose(1001L, "trace-of-other");

    assertThat(response.getData().involvementReason()).isEqualTo("TRACE_CLAIM_MISMATCH");
    assertThat(response.getData().traceId()).isEmpty();
    verify(service).disclose(1001L, "trace-of-other");
  }
}
