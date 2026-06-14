package com.bone.integration.infrastructure.notification;

import com.bone.integration.application.event.port.IntegrationEventNotifier;
import com.bone.platform.alert.AlertLevel;
import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.AlertService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AlertIntegrationEventNotifier implements IntegrationEventNotifier {

  private final AlertService alertService;

  @Override
  public void sendInfo(String action, String title, String content, String businessId) {
    dispatch(AlertLevel.INFO, action, title, content, businessId);
  }

  @Override
  public void sendWarning(String action, String title, String content, String businessId) {
    dispatch(AlertLevel.MEDIUM, action, title, content, businessId);
  }

  @Override
  public void sendHigh(String action, String title, String content, String businessId) {
    dispatch(AlertLevel.HIGH, action, title, content, businessId);
  }

  private void dispatch(
      AlertLevel level, String action, String title, String content, String businessId) {
    try {
      AlertMessage message =
          AlertMessage.builder()
              .level(level)
              .title(title)
              .content(content)
              .businessId(businessId)
              .context(Map.of("action", action, "module", "integration"))
              .build();
      alertService.sendAlert(message);
    } catch (Exception ex) {
      log.error(
          "integration alert dispatch failed: action={}, businessId={}", action, businessId, ex);
    }
  }
}
