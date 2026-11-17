package com.bone.procurement.transaction.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.transaction.adapter.web.controller.PurchaseOrderController;
import com.bone.procurement.transaction.adapter.web.controller.RequisitionController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** TDD RED：锁定 6 端点的 method / path（契约漂移即红）。 */
class ProcurementControllerContractTest {

  @Test
  void 请购控制器两个端点() {
    RequisitionController.class.getDeclaredMethods();
    assertThat(requestMapping(RequisitionController.class))
        .isEqualTo("/api/v1/procurement/requisitions");
    assertThat(hasPost(RequisitionController.class, "")).isTrue();
    assertThat(hasPost(RequisitionController.class, "/{no}/convert")).isTrue();
  }

  @Test
  void 采购订单控制器五个端点含列表() {
    assertThat(requestMapping(PurchaseOrderController.class))
        .isEqualTo("/api/v1/procurement/purchase-orders");
    assertThat(hasPost(PurchaseOrderController.class, "")).isTrue();
    assertThat(hasPost(PurchaseOrderController.class, "/{no}/submit")).isTrue();
    assertThat(hasPost(PurchaseOrderController.class, "/{no}/send")).isTrue();
    assertThat(hasGet(PurchaseOrderController.class, "")).isTrue();
    assertThat(hasGet(PurchaseOrderController.class, "/{no}")).isTrue();
  }

  @Test
  void 所有端点都声明权限码() {
    for (Class<?> c : new Class<?>[] {RequisitionController.class, PurchaseOrderController.class}) {
      for (Method m : c.getDeclaredMethods()) {
        if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(GetMapping.class)) {
          org.springframework.security.access.prepost.PreAuthorize pa =
              m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
          assertThat(pa).as(c.getSimpleName() + "." + m.getName() + " 缺 @PreAuthorize").isNotNull();
          assertThat(pa.value()).contains("procurement:");
        }
      }
    }
  }

  private static String requestMapping(Class<?> c) {
    return c.getAnnotation(RequestMapping.class).value()[0];
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
