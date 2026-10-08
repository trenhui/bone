package com.bone.integration.infrastructure.notification;

import com.bone.integration.application.port.out.IntegrationEventNotifierPort;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompositeIntegrationEventNotifier implements IntegrationEventNotifierPort {

  private final List<IntegrationEventNotifierPort> delegates;

  @Override
  public void sendInfo(String action, String title, String content, String businessId) {
    delegates.forEach(d -> d.sendInfo(action, title, content, businessId));
  }

  @Override
  public void sendWarning(String action, String title, String content, String businessId) {
    delegates.forEach(d -> d.sendWarning(action, title, content, businessId));
  }

  @Override
  public void sendHigh(String action, String title, String content, String businessId) {
    delegates.forEach(d -> d.sendHigh(action, title, content, businessId));
  }
}
