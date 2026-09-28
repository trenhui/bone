package com.bone.system.domain.model.config;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 系统配置变更历史（投影表，ADR-0030 C2：聚合内事件侧不可达，走投影）。
 *
 * <p>每次配置创建 / 更新由 {@link ConfigApplicationService} 写入一条，供 {@code /config/{id}/history} 读取。
 * 历史为只追加流水，不承接业务命令，故无需软删 / 乐观锁。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_config_history")
public class ConfigHistory extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long configId;
  private String configKey;
  private String oldValue;
  private String newValue;
  private String changeType;
  private String operator;

  /** 操作人所属租户（修复 R2：不再隐式依赖 TenantContext，事件显式携带，脱离主链路也不丢租户）。 */
  private Long tenantId;

  /** 幂等事件 ID（修复 R7：事件重放时按此去重，避免重复历史行）。 */
  private String eventId;

  private LocalDateTime createdAt;

  public static ConfigHistory of(
      Long configId,
      String configKey,
      String oldValue,
      String newValue,
      String changeType,
      String operator,
      Long tenantId,
      String eventId) {
    ConfigHistory history = new ConfigHistory();
    history.configId = configId;
    history.configKey = configKey;
    history.oldValue = oldValue;
    history.newValue = newValue;
    history.changeType = changeType;
    history.operator = operator;
    history.tenantId = tenantId;
    history.eventId = eventId;
    history.createdAt = LocalDateTime.now();
    return history;
  }

  /**
   * 审计摘要：人读的一行变更描述，用于日志 / 审计流水展示。
   *
   * <p>创建以 {@code (init)} 占位旧值；更新则展示「旧值 -> 新值」。属领域行为（非取值器）， 供纯单测门禁（R8）与运行时审计使用。
   */
  public String auditSummary() {
    String old = oldValue == null ? "(init)" : oldValue;
    return changeType + " " + configKey + ": " + old + " -> " + newValue;
  }
}
