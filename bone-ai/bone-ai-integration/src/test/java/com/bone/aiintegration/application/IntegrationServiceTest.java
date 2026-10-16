package com.bone.aiintegration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aiintegration.domain.gateway.ExternalSystemPort;
import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.model.IntegrationMode;
import org.junit.jupiter.api.Test;

class IntegrationServiceTest {

  private final ExternalSystemPort port = mock(ExternalSystemPort.class);
  private final IntegrationService service = new IntegrationService(port);

  private ExternalCall call(IntegrationMode mode) {
    return new ExternalCall("c1", 42L, "SYS", "https://ext/api", "{}", mode, "corr-1");
  }

  private void stubOk() {
    when(port.call(any()))
        .thenReturn(new ExternalCallOutcome("c1", "SYS", 200, "{\"ok\":true}", 12L, null));
  }

  @Test
  void automated_marks_result_used() {
    stubOk();
    ExternalCallResult r = service.invoke(call(IntegrationMode.AUTOMATED));
    assertThat(r.used()).isTrue();
    assertThat(r.shadow()).isFalse();
    assertThat(r.httpStatus()).isEqualTo(200);
  }

  @Test
  void shadow_observes_only_not_used() {
    stubOk();
    ExternalCallResult r = service.invoke(call(IntegrationMode.SHADOW));
    assertThat(r.used()).isFalse();
    assertThat(r.shadow()).isTrue();
  }

  @Test
  void assisted_awaits_human_confirmation() {
    stubOk();
    ExternalCallResult r = service.invoke(call(IntegrationMode.ASSISTED));
    assertThat(r.used()).isFalse();
    assertThat(r.shadow()).isFalse();
  }

  @Test
  void delegates_to_port_exactly_once() {
    stubOk();
    service.invoke(call(IntegrationMode.AUTOMATED));
    verify(port).call(any());
  }
}
