package com.bone.procurement.business.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.business.InMemoryBusinessRepository;
import com.bone.procurement.business.adapter.web.controller.BusinessController;
import com.bone.procurement.business.application.BusinessApplicationService;
import com.bone.procurement.business.domain.gateway.BusinessIdentityPort;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class BusinessEndpointContractTest {

  private static final Date NOW = Date.from(Instant.parse("2026-11-26T00:00:00Z"));

  @Test
  @DisplayName("授标端点声明权限注解，且请求体身份字段不参与赋值")
  void awardEndpointUsesLoginIdentity() throws Exception {
    BusinessApplicationService service =
        new BusinessApplicationService(
            new InMemoryBusinessRepository(), () -> new BusinessIdentityPort.Actor(1L, "alice"));
    BusinessController controller =
        new BusinessController(service, () -> new BusinessIdentityPort.Actor(1L, "alice"));

    Method method =
        BusinessController.class.getMethod(
            "award", String.class, BusinessController.AwardRequest.class);
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).contains("procurement:rfx:approve");

    assertThatThrownBy(
            () ->
                controller.award(
                    "RFX-1", new BusinessController.AwardRequest(1L, "SUP-1", "理由", "bob")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("冒名");

    var response =
        controller.award("RFX-1", new BusinessController.AwardRequest(1L, "SUP-1", "理由", "alice"));
    assertThat(response.getData().confirmedBy()).isEqualTo("alice");
  }

  @Test
  @DisplayName("读端点拒绝跨租户查询，不回显他方业务事实")
  void readEndpointRejectsCrossTenant() {
    BusinessApplicationService service =
        new BusinessApplicationService(
            new InMemoryBusinessRepository(), () -> new BusinessIdentityPort.Actor(1L, "alice"));
    BusinessController controller =
        new BusinessController(service, () -> new BusinessIdentityPort.Actor(1L, "alice"));

    assertThatThrownBy(() -> controller.findAward("RFX-1", 2L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("跨租户");
  }

  @Test
  @DisplayName("响应 DTO 为字段白名单，不携带 AI output 或 PII 字段")
  void responseDtoIsWhitelisted() {
    assertThat(
            Arrays.stream(BusinessController.AwardView.class.getDeclaredFields())
                .map(field -> field.getName()))
        .noneMatch(name -> name.toLowerCase().contains("output"))
        .noneMatch(name -> name.toLowerCase().contains("pii"))
        .noneMatch(name -> name.toLowerCase().contains("reasonjson"));
  }
}
