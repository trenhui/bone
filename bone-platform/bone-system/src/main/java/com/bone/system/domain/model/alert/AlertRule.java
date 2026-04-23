package com.bone.system.domain.model.alert;

import com.bone.core.domain.AggregateRoot;
import com.bone.system.domain.model.alert.event.AlertRuleCreatedEvent;
import com.bone.system.domain.model.alert.event.AlertRuleUpdatedEvent;
import com.bone.system.domain.model.alert.vo.AlertLevel;
import com.bone.system.domain.model.alert.vo.MetricName;
import com.bone.system.domain.model.alert.vo.Threshold;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则聚合根
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AlertRule extends AggregateRoot<Long> {
    private String name;
    private String description;
    private MetricName metricName;
    private Threshold threshold;
    private AlertLevel alertLevel;
    private List<String> notificationChannels;
    private boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建告警规则
     *
     * @param name                 规则名称
     * @param description          描述
     * @param metricName           指标名称
     * @param threshold            阈值
     * @param alertLevel           告警级别
     * @param notificationChannels 通知渠道
     * @return 告警规则
     */
    public static AlertRule create(String name, String description, MetricName metricName, 
                                   Threshold threshold, AlertLevel alertLevel, 
                                   List<String> notificationChannels) {
        AlertRule rule = new AlertRule();
        rule.name = name;
        rule.description = description;
        rule.metricName = metricName;
        rule.threshold = threshold;
        rule.alertLevel = alertLevel;
        rule.notificationChannels = notificationChannels;
        rule.enabled = true;
        rule.createTime = LocalDateTime.now();
        rule.updateTime = LocalDateTime.now();
        rule.addDomainEvent(new AlertRuleCreatedEvent(rule));
        return rule;
    }

    /**
     * 更新告警规则
     *
     * @param name                 规则名称
     * @param description          描述
     * @param threshold            阈值
     * @param alertLevel           告警级别
     * @param notificationChannels 通知渠道
     */
    public void update(String name, String description, Threshold threshold, 
                       AlertLevel alertLevel, List<String> notificationChannels) {
        this.name = name;
        this.description = description;
        this.threshold = threshold;
        this.alertLevel = alertLevel;
        this.notificationChannels = notificationChannels;
        this.updateTime = LocalDateTime.now();
        addDomainEvent(new AlertRuleUpdatedEvent(getId(), name, description, threshold.value(), alertLevel, notificationChannels));
    }

    /**
     * 启用告警规则
     */
    public void enable() {
        this.enabled = true;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 禁用告警规则
     */
    public void disable() {
        this.enabled = false;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断是否应该触发告警
     *
     * @param currentValue 当前值
     * @return 是否应该触发
     */
    public boolean shouldTrigger(double currentValue) {
        return enabled && currentValue >= threshold.value();
    }

    /**
     * 设置ID（供SDK回填使用）
     *
     * @param id ID值
     */
    public void setId(Long id) {
        super.setId(id);
    }
}
