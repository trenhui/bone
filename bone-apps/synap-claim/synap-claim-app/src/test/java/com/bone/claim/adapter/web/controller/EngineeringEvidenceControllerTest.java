package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.application.EngineeringEvidenceService;
import com.bone.claim.application.EngineeringEvidenceService.EvidenceCriterion;
import com.bone.claim.application.EngineeringEvidenceService.EvidenceView;
import com.bone.core.model.ApiResponse;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 工程证据只读端点契约：锁住「只读」「复用只读权限」「返回 ApiResponse」三条边界。 */
class EngineeringEvidenceControllerTest {

  @Test
  @DisplayName("权限点复用既有 claim:claims:read，不得新增写权限")
  void usesExistingReadAuthority() throws NoSuchMethodException {
    Method method = EngineeringEvidenceController.class.getMethod("evidence");
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("claim:claims:read");
    assertThat(annotation.value()).doesNotContain("write");
  }

  @Test
  @DisplayName("只读端点只调用 view 一次，返回 ApiResponse 包装证据视图，无额外副作用")
  void readOnlyDelegateHasNoSideEffect() {
    EngineeringEvidenceService service = mock(EngineeringEvidenceService.class);
    EvidenceView view =
        new EvidenceView(
            List.of(new EvidenceCriterion("C2_RULE_DECLARATION", "EVIDENCED", "")), "note");
    when(service.view()).thenReturn(view);

    ApiResponse<EvidenceView> response = new EngineeringEvidenceController(service).evidence();

    assertThat(response).isNotNull();
    assertThat(response.getData()).isSameAs(view);
    verify(service, times(1)).view();
    verifyNoMoreInteractions(service);
  }
}
