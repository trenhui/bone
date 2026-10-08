package com.bone.system.domain.model.alert;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.alert.event.AlertRuleCreatedEvent;
import com.bone.system.domain.model.alert.event.AlertRuleUpdatedEvent;
import com.bone.system.domain.model.alert.valueobject.AlertLevel;
import com.bone.system.domain.model.alert.valueobject.MetricName;
import com.bone.system.domain.model.alert.valueobject.Threshold;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_alert_rule")
public class AlertRule extends com.bone.core.domain.AggregateRoot<Long> {

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  @Deleted private Boolean deleted = false;

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @com.bone.metadata.sdk.domain.annotation.Version private Long version;

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
