package com.bone.system.infrastructure.event;

import com.bone.system.domain.model.config.ConfigHistory;
import com.bone.system.domain.model.config.event.ConfigChangedEvent;
import com.bone.system.domain.model.config.event.ConfigCreatedEvent;
import com.bone.system.domain.repository.ConfigHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p>可靠性加固（§10.8 R1/R2/R7/R8）：
 *
 * <ul>
 *   <li><b>R1 真实操作人</b>：事件已携带由应用层从 JWT 主体解析的 {@code operator}，不再硬编码 "admin"。
 *   <li><b>R2 显式租户</b>：事件携带 {@code tenantId}，历史行不再隐式依赖 TenantContext（脱离主链路 / Outbox 重放也不丢租户）。
 *   <li><b>R7 幂等</b>：每条事件带 UUID {@code eventId}，落库前先判重；配合 {@code sys_config_history.event_id}
 *       唯一约束，事件重放不插重复历史。
 *   <li><b>R8 best-effort</b>：历史落库失败仅告警、不影响主配置写响应（非关键路径）。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigHistoryProjector {

  private final ConfigHistoryRepository configHistoryRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onCreated(ConfigCreatedEvent event) {
    project(
        event.eventId(),
        () ->
            configHistoryRepository.save(
                ConfigHistory.of(
                    event.configId(),
                    event.configKey(),
                    null,
                    event.configValue(),
                    "CREATE",
                    event.operator(),
                    event.tenantId(),
                    event.eventId())));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onChanged(ConfigChangedEvent event) {
    project(
        event.eventId(),
        () ->
            configHistoryRepository.save(
                ConfigHistory.of(
                    event.configId(),
                    event.configKey(),
                    event.oldValue(),
                    event.newValue(),
                    "UPDATE",
                    event.operator(),
                    event.tenantId(),
                    event.eventId())));
  }

  /** 幂等 + best-effort：重放事件先判重，落库异常仅告警（R7 + R8）。 */
  private void project(String eventId, Runnable save) {
    try {
      Long existed = configHistoryRepository.countByEventId(eventId);
      if (existed != null && existed > 0) {
        return;
      }
      save.run();
    } catch (Exception e) {
      log.warn("配置变更历史投影失败, eventId={}: {}", eventId, e.getMessage());
    }
  }
}
