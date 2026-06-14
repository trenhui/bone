package com.bone.integration.infrastructure.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.bone.platform.alert.AlertLevel;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertIntegrationEventNotifierTest {

  @Mock private AlertService alertService;

  @InjectMocks private AlertIntegrationEventNotifier notifier;

  @Test
  void sendHigh_dispatchesAlertMessage() {
    notifier.sendHigh("execution.failed.alert", "流程执行失败", "flowId=1", "99");

    ArgumentCaptor<AlertMessage> captor = ArgumentCaptor.forClass(AlertMessage.class);
    verify(alertService).sendAlert(captor.capture());
    AlertMessage message = captor.getValue();
    assertEquals(AlertLevel.HIGH, message.getLevel());
    assertEquals("99", message.getBusinessId());
    assertEquals("execution.failed.alert", message.getContext().get("action"));
  }
}
