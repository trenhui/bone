package com.bone.integration.infrastructure.notification;

import com.bone.integration.application.event.port.IntegrationEventNotifier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingIntegrationEventNotifier implements IntegrationEventNotifier {

  @Override
  public void sendInfo(String action, String title, String content, String businessId) {
    log.info(
        "integration event: action={}, title={}, businessId={}, content={}",
        action,
        title,
        businessId,
        content);
  }

  @Override
  public void sendWarning(String action, String title, String content, String businessId) {
    log.warn(
        "integration event: action={}, title={}, businessId={}, content={}",
        action,
        title,
        businessId,
        content);
  }

  @Override
  public void sendHigh(String action, String title, String content, String businessId) {
    log.warn(
        "integration alert: action={}, title={}, businessId={}, content={}",
        action,
        title,
        businessId,
        content);
  }
}
