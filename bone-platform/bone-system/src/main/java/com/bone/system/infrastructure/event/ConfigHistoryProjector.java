package com.bone.system.infrastructure.event;

import com.bone.system.domain.model.config.ConfigHistory;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.repository.ConfigHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 配置变更历史投影（ADR-0030 C2：聚合内事件侧不可达，走投影）。
 *
 * <p>{@code SystemConfig} 在创建 / 更新时积下 {@link ConfigCreatedEvent} / {@link ConfigChangedEvent}，
 * 本订阅器在写事务提交后（AFTER_COMMIT）以独立事务（REQUIRES_NEW）落 {@code sys_config_history} 投影表。
 *
 * <p>为何不写在用例内：跨聚合写（SystemConfig + ConfigHistory）违反 R9「一事务一聚合」。放进 AFTER_COMMIT
 * 订阅器既把历史写与主配置写解耦成两个独立事务，又保证主配置写路径不受历史落库失败拖累（历史为只追加流水，非关键路径）。
 */
@Component
@RequiredArgsConstructor
public class ConfigHistoryProjector {

  /** 尚未接入操作人上下文前的审计占位符（与 {@code ConfigApplicationService.OPERATOR} 一致）。 */
  private static final String OPERATOR = "admin";

  private final ConfigHistoryRepository configHistoryRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onCreated(ConfigCreatedEvent event) {
    configHistoryRepository.save(
        ConfigHistory.of(
            event.configId(), event.configKey(), null, event.configValue(), "CREATE", OPERATOR));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onChanged(ConfigChangedEvent event) {
    configHistoryRepository.save(
        ConfigHistory.of(
            event.configId(),
            event.configKey(),
            event.oldValue(),
            event.newValue(),
            "UPDATE",
            event.operator()));
  }
}
