package com.bone.integration.application.event.port;

/** 集成领域事件后续通知端口（出站适配器由 infrastructure 实现）。 */
public interface IntegrationEventNotifier {

  void sendInfo(String action, String title, String content, String businessId);

  void sendWarning(String action, String title, String content, String businessId);

  void sendHigh(String action, String title, String content, String businessId);
}
