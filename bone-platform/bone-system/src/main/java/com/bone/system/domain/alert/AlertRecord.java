package com.bone.system.domain.alert;

import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.alert.event.AlertResolvedEvent;
import com.bone.system.domain.alert.vo.AlertLevel;
import com.bone.system.domain.alert.vo.AlertStatus;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_alert_event")
public class AlertRecord extends com.bone.core.domain.AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "rule_id")
  private Long alertRuleId;

  @Column(name = "rule_name")
  private String ruleName;

  private String metricName;

  @Column(name = "current_value")
  private double actualValue;

  @Column(name = "threshold_value")
  private double threshold;

  private AlertLevel alertLevel;
  private String message;
  private AlertStatus status;
  private LocalDateTime createdAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolveTime;

  public static AlertRecord create(
      Long id,
      Long alertRuleId,
      String ruleName,
      String metricName,
      double actualValue,
      double threshold,
      AlertLevel alertLevel,
      String message) {
    AlertRecord event = new AlertRecord();
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
