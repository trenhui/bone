package com.bone.system.domain.alert;

import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.alert.event.AlertRuleCreatedEvent;
import com.bone.system.domain.model.alert.event.AlertRuleUpdatedEvent;
import com.bone.system.domain.model.alert.vo.AlertLevel;
import com.bone.system.domain.model.alert.vo.MetricName;
import com.bone.system.domain.model.alert.vo.Threshold;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_alert_rule")
public class AlertRule extends com.bone.core.domain.AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String name;
  private String description;
  private MetricName metricName;

  @Column(name = "threshold_value")
  private Threshold threshold;

  private AlertLevel alertLevel;
  private List<String> notificationChannels;
  private boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static AlertRule create(
      Long id,
      String name,
      String description,
      MetricName metricName,
      Threshold threshold,
      AlertLevel alertLevel,
      List<String> notificationChannels) {
    AlertRule rule = new AlertRule();
    rule.id = id;
    rule.name = name;
    rule.description = description;
    rule.metricName = metricName;
    rule.threshold = threshold;
    rule.alertLevel = alertLevel;
    rule.notificationChannels = notificationChannels;
    rule.enabled = true;
    rule.createdAt = LocalDateTime.now();
    rule.updatedAt = LocalDateTime.now();
    rule.addDomainEvent(new AlertRuleCreatedEvent(rule));
    return rule;
  }

  public void update(
      String name,
      String description,
      Threshold threshold,
      AlertLevel alertLevel,
      List<String> notificationChannels) {
    this.name = name;
    this.description = description;
    this.threshold = threshold;
    this.alertLevel = alertLevel;
    this.notificationChannels = notificationChannels;
    this.updatedAt = LocalDateTime.now();
    addDomainEvent(
        new AlertRuleUpdatedEvent(
            getId(), name, description, threshold.value(), alertLevel, notificationChannels));
  }

  public void enable() {
    this.enabled = true;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    this.enabled = false;
    this.updatedAt = LocalDateTime.now();
  }

  public boolean shouldTrigger(double currentValue) {
    return enabled && currentValue >= threshold.value();
  }
}
