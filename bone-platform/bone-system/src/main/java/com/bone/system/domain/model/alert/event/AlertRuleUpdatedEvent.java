package com.bone.system.domain.model.alert.event;

import com.bone.core.domain.DomainEvent;
import com.bone.system.domain.model.alert.vo.AlertLevel;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则更新事件
 */
public record AlertRuleUpdatedEvent(
        Long alertRuleId,
        String name,
        String description,
        double threshold,
        AlertLevel alertLevel,
        List<String> notificationChannels,
        LocalDateTime eventTime
) implements DomainEvent {

    public AlertRuleUpdatedEvent(Long alertRuleId, String name, String description,
                                  double threshold, AlertLevel alertLevel, List<String> notificationChannels) {
        this(alertRuleId, name, description, threshold, alertLevel, notificationChannels, LocalDateTime.now());
    }
}
