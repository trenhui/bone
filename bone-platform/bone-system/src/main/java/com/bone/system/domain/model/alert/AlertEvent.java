package com.bone.system.domain.model.alert;

import com.bone.core.domain.AggregateRoot;
import com.bone.system.domain.model.alert.event.AlertResolvedEvent;
import com.bone.system.domain.model.alert.event.AlertTriggeredEvent;
import com.bone.system.domain.model.alert.vo.AlertLevel;
import com.bone.system.domain.model.alert.vo.AlertStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警事件聚合根
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AlertEvent extends AggregateRoot<Long> {
    private Long alertRuleId;
    private String ruleName;
    private String metricName;
    private double actualValue;
    private double threshold;
    private AlertLevel alertLevel;
    private String message;
    private AlertStatus status;
    private LocalDateTime createTime;
    private LocalDateTime resolveTime;

    /**
     * 创建告警事件
     *
     * @param alertRuleId 告警规则ID
     * @param ruleName    规则名称
     * @param metricName  指标名称
     * @param actualValue 实际值
     * @param threshold   阈值
     * @param alertLevel  告警级别
     * @param message     消息
     * @return 告警事件
     */
    public static AlertEvent create(Long alertRuleId, String ruleName, String metricName, 
                                     double actualValue, double threshold, AlertLevel alertLevel, 
                                     String message) {
        AlertEvent event = new AlertEvent();
        event.alertRuleId = alertRuleId;
        event.ruleName = ruleName;
        event.metricName = metricName;
        event.actualValue = actualValue;
        event.threshold = threshold;
        event.alertLevel = alertLevel;
        event.message = message;
        event.status = AlertStatus.TRIGGERED;
        event.createTime = LocalDateTime.now();
        return event;
    }

    /**
     * 解决告警
     */
    public void resolve() {
        this.status = AlertStatus.RESOLVED;
        this.resolveTime = LocalDateTime.now();
        addDomainEvent(new AlertResolvedEvent(this.id, this.alertRuleId, this.ruleName));
    }

    /**
     * 设置ID（供SDK回填使用）
     *
     * @param id ID值
     */
    void setId(Long id) {
        this.id = id;
    }
}
