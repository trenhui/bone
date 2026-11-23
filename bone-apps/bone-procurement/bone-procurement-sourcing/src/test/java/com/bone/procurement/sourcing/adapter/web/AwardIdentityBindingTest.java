package com.bone.procurement.sourcing.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.procurement.sourcing.adapter.web.controller.SourcingController;
import com.bone.procurement.sourcing.adapter.web.controller.SourcingController.AwardRfxRequest;
import com.bone.procurement.sourcing.application.SourcingApplicationService;
import com.bone.procurement.sourcing.domain.gateway.AwardIdentityPort;
import com.bone.procurement.sourcing.domain.gateway.AwardIdentityPort.AwardIdentity;
import org.junit.jupiter.api.Test;

/** RFx 人工授标身份绑定：确认人只取登录态，冒名/匿名/跨租户拒绝且不落库。 */
class AwardIdentityBindingTest {

  private final SourcingApplicationService service = mock(SourcingApplicationService.class);
  private final AwardIdentityPort identityPort = mock(AwardIdentityPort.class);
  private final SourcingController controller = new SourcingController(service, identityPort);

  @Test
  void 匿名授标被拒且不落库() {
    when(identityPort.current()).thenReturn(null);

    assertThatThrownBy(() -> controller.awardRfx("RFX-1", new AwardRfxRequest(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 请求体冒名授标被拒且不落库() {
    when(identityPort.current()).thenReturn(new AwardIdentity(1L, "alice"));

    assertThatThrownBy(() -> controller.awardRfx("RFX-1", new AwardRfxRequest(1L, "bob")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 请求体跨租户授标被拒且不落库() {
    when(identityPort.current()).thenReturn(new AwardIdentity(1L, "alice"));

    assertThatThrownBy(() -> controller.awardRfx("RFX-1", new AwardRfxRequest(2L, "alice")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 登录态身份字段不全时拒绝且不落库() {
    when(identityPort.current()).thenReturn(new AwardIdentity(1L, "  "));

    assertThatThrownBy(() -> controller.awardRfx("RFX-1", new AwardRfxRequest(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 具名登录态按权威值授标() {
    when(identityPort.current()).thenReturn(new AwardIdentity(1L, "alice"));

    controller.awardRfx("RFX-1", new AwardRfxRequest(1L, "alice"));

    org.mockito.ArgumentCaptor<String> operator = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(service)
        .awardByRfxNo(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq("RFX-1"),
            operator.capture());
    assertThat(operator.getValue()).isEqualTo("alice");
  }
}
