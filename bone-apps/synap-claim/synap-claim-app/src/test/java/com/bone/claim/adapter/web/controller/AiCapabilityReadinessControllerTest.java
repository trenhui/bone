package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiCapabilityReadinessService;
import com.bone.claim.application.AiCapabilityReadinessService.ReadinessSnapshot;
import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * AI 治理只读端点的契约测试：锁住「只读」与「权限点」两条边界。
 *
 * <p>端点是运维/审计面，一旦误挂写权限或误加副作用（例如顺手触发一次能力探测），就把一个只读治理视图 变成有业务影响的调用；因此这里显式断言只调用 {@code snapshot()}
 * 一次，且权限复用既有只读权限点。
 */
class AiCapabilityReadinessControllerTest {

  @Test
  @DisplayName("权限点必须复用既有 claim:claims:read，不得新增/误挂写权限")
  void usesExistingReadAuthority() throws NoSuchMethodException {
    Method method = AiCapabilityReadinessController.class.getMethod("readiness");
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("claim:claims:read");
    assertThat(annotation.value()).doesNotContain("write");
  }

  @Test
  @DisplayName("只读端点只调用 snapshot 一次，不产生任何额外交互")
  void readOnlyDelegateHasNoSideEffect() {
    AiCapabilityReadinessService service = mock(AiCapabilityReadinessService.class);
    ReadinessSnapshot snapshot =
        new ReadinessSnapshot(
            "HIGH",
            false,
            List.of(AiCapabilityDeclaration.ready("C2_RULE", "test")),
            List.of("AUTO_APPROVE_TOGGLE_OFF"),
            false,
            "scope");
    when(service.snapshot()).thenReturn(snapshot);

    new AiCapabilityReadinessController(service).readiness();

    verify(service, times(1)).snapshot();
    verifyNoMoreInteractions(service);
  }
}
