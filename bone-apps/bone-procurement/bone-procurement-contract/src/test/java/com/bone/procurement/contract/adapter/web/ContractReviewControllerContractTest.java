package com.bone.procurement.contract.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.contract.adapter.web.controller.ContractReviewController;
import com.bone.procurement.contract.adapter.web.controller.ContractReviewSubmitRequest;
import com.bone.procurement.contract.application.AiDisclosureView;
import com.bone.procurement.contract.application.ContractReviewApplicationService;
import com.bone.procurement.contract.application.HumanOverrideInput;
import com.bone.procurement.contract.application.ReviewReport;
import com.bone.procurement.contract.application.SubmitReviewCommand;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** HTTP 契约：路径 / 方法 / 权限码 / ApiResponse 包装 / 能力开关条件。 */
class ContractReviewControllerContractTest {

  @Test
  void 四端点路径与方法锁定() {
    assertThat(ContractReviewController.class.getAnnotation(RequestMapping.class).value()[0])
        .isEqualTo("/api/v1/procurement/contract-reviews");
    assertThat(hasPost(ContractReviewController.class, "")).isTrue();
    assertThat(hasGet(ContractReviewController.class, "/{reviewId}")).isTrue();
    assertThat(hasGet(ContractReviewController.class, "/{reviewId}/disclosure")).isTrue();
    assertThat(hasPost(ContractReviewController.class, "/{reviewId}/override")).isTrue();
  }

  @Test
  void 写读权限码锁定() {
    assertAuthority("submit", "procurement:contract:review:write");
    assertAuthority("override", "procurement:contract:review:write");
    assertAuthority("getReport", "procurement:contract:review:read");
    assertAuthority("disclosure", "procurement:contract:review:read");
  }

  @Test
  void 所有端点返回ApiResponse包装() {
    for (Method m : ContractReviewController.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(GetMapping.class)) {
        assertThat(m.getReturnType())
            .as(m.getName() + " 必须用 ApiResponse<T> 包装")
            .isEqualTo(ApiResponse.class);
      }
    }
  }

  @Test
  void 直接调用端点锁定响应包装() {
    ContractReviewApplicationService service = mock(ContractReviewApplicationService.class);
    ReviewReport report =
        new ReviewReport(
            "rv-1",
            "CT-1",
            "HUMAN_REVIEW",
            AiParticipation.UNKNOWN,
            RiskLevel.UNKNOWN,
            false,
            null);
    when(service.submit(any(SubmitReviewCommand.class))).thenReturn(report);
    when(service.getReport(anyString())).thenReturn(report);
    when(service.override(anyString(), any(HumanOverrideInput.class))).thenReturn(report);
    when(service.disclosure(anyString()))
        .thenReturn(new AiDisclosureView("rv-1", "UNKNOWN", "无法确认", false, null));
    ContractReviewController controller = new ContractReviewController(service);

    ApiResponse<?> submitted =
        controller.submit(new ContractReviewSubmitRequest("CT-1", "idem-1", "src://c", "doc"));
    assertThat(submitted.isSuccess()).isTrue();
    assertThat(submitted.getData()).isEqualTo(report);
    assertThat(controller.getReport("rv-1").getData()).isEqualTo(report);
    assertThat(
            controller
                .override("rv-1", new HumanOverrideInput("r", "reason", RiskLevel.HIGH))
                .getData())
        .isEqualTo(report);
    assertThat(controller.disclosure("rv-1").getData()).isInstanceOf(AiDisclosureView.class);
  }

  private static void assertAuthority(String methodName, String authority) {
    Method method =
        Arrays.stream(ContractReviewController.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
    PreAuthorize pa = method.getAnnotation(PreAuthorize.class);
    assertThat(pa).as(methodName + " 缺 @PreAuthorize").isNotNull();
    assertThat(pa.value()).contains(authority);
  }

  private static boolean hasPost(Class<?> c, String path) {
    return Arrays.stream(c.getDeclaredMethods())
        .anyMatch(
            m ->
                m.isAnnotationPresent(PostMapping.class)
                    && Arrays.asList(m.getAnnotation(PostMapping.class).value()).contains(path));
  }

  private static boolean hasGet(Class<?> c, String path) {
    return Arrays.stream(c.getDeclaredMethods())
        .anyMatch(
            m ->
                m.isAnnotationPresent(GetMapping.class)
                    && Arrays.asList(m.getAnnotation(GetMapping.class).value()).contains(path));
  }
}
