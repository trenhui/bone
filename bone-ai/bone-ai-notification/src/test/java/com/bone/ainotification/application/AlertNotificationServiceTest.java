package com.bone.ainotification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ainotification.domain.gateway.NotificationAckSink;
import com.bone.ainotification.domain.gateway.NotificationPort;
import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationAck;
import com.bone.ainotification.domain.model.NotificationChannel;
import com.bone.ainotification.domain.model.NotificationResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AlertNotificationServiceTest {

  @Test
  void dispatch_delegates_to_port() {
    NotificationPort port = mock(NotificationPort.class);
    NotificationAckSink sink = mock(NotificationAckSink.class);
    AlertNotificationApplicationService service =
        new AlertNotificationApplicationService(port, sink);

    AlertEvent event = new AlertEvent("A1", 42L, "HIGH", "t", "b", "bone-ai-rules", Instant.now());
    NotificationResult result =
        new NotificationResult(NotificationChannel.WEBHOOK, true, "ok", null);
    when(port.dispatch(any(), any(), any())).thenReturn(result);

    NotificationResult got = service.dispatch(event, NotificationChannel.WEBHOOK, "https://hook");
    assertThat(got).isSameAs(result);
    verify(port).dispatch(event, NotificationChannel.WEBHOOK, "https://hook");
  }

  @Test
  void acknowledge_writes_back_via_sink() {
    NotificationPort port = mock(NotificationPort.class);
    NotificationAckSink sink = mock(NotificationAckSink.class);
    AlertNotificationApplicationService service =
        new AlertNotificationApplicationService(port, sink);

    NotificationAck ack = new NotificationAck("A1", 42L, "u1", Instant.now(), "handled");
    service.acknowledge(ack);
    verify(sink).recordAck(ack);
  }
}
