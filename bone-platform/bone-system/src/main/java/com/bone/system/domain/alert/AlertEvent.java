package com.bone.system.domain.alert;

import com.bone.system.domain.alert.event.AlertResolvedEvent;
import com.bone.system.domain.alert.event.AlertTriggeredEvent;
import com.bone.system.domain.alert.vo.AlertLevel;
import com.bone.system.domain.alert.vo.AlertStatus;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("sys_alert_event")
public class AlertEvent extends com.bone.core.domain.AggregateRoot<Long> {
    private Long id;
    private Long alertRuleId;
    private String ruleName;
    private String metricName;
    private double actualValue;
    private double threshold;
    private AlertLevel alertLevel;
    private String message;
    private AlertStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolveTime;

    public static AlertEvent create(Long id, Long alertRuleId, String ruleName, String metricName,
                                     double actualValue, double threshold, AlertLevel alertLevel,
                                     String message) {
        AlertEvent event = new AlertEvent();
        event.id = id;
        event.alertRuleId = alertRuleId;
        event.ruleName = ruleName;
        event.metricName = metricName;
        event.actualValue = actualValue;
        event.threshold = threshold;
        event.alertLevel = alertLevel;
        event.message = message;
        event.status = AlertStatus.TRIGGERED;
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public void resolve() {
        this.status = AlertStatus.RESOLVED;
        this.resolveTime = LocalDateTime.now();
        addDomainEvent(new AlertResolvedEvent(getId(), this.alertRuleId, this.ruleName));
    }
}
