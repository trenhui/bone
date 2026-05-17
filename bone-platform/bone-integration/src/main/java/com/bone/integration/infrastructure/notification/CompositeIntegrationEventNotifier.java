package com.bone.integration.infrastructure.notification;

import com.bone.integration.application.event.port.IntegrationEventNotifier;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CompositeIntegrationEventNotifier implements IntegrationEventNotifier {

    private final List<IntegrationEventNotifier> delegates;

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
