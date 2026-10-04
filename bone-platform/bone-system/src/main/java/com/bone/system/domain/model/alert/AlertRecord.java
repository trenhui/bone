package com.bone.system.domain.model.alert;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.PhysicalDelete;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.alert.event.AlertResolvedEvent;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;
import com.bone.system.domain.model.alert.valueobject.AlertStatus;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 告警事件聚合（落库表 {@code sys_alert_event}）。
 *
 * <p><b>为什么类名叫 AlertRecord、表叫 sys_alert_event</b>：这是历史遗留的命名口径不一致——聚合名沿用
 * 「告警记录」的业务叫法，表名按「事件」语义落库（一条规则触发产生一条事件）。二者指同一事物， 与同包 {@code AlertRule}（表 {@code
 * sys_alert_rule}）的「聚合名 ↔ 表名」对应方式不同。
 *
 * <p><b>新增代码请以表名语义为准</b>：局部变量、DTO、方法名用 {@code alertEvent}，不要再扩散 {@code record} 口径；本类改名属
 * L3（聚合重命名），需架构师审批后统一执行。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@PhysicalDelete(reason = "告警事件流水：append-only，按保留期归档清理（规则配置在 sys_alert_rule，不在本表）")
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

  /**
   * 告警持续期间更新最新实测值（定时评估器去重路径）。
   *
   * <p>同一告警事件（episode）内实测值会随每次评估变化；原地更新 {@code current_value} 而不是 每周期插一条新事件，保持告警列表「一条规则一个进行中事件」的语义。
   */
  public void observe(double actualValue, String message) {
    this.actualValue = actualValue;
    if (message != null && !message.isBlank()) {
      this.message = message;
    }
  }
}
