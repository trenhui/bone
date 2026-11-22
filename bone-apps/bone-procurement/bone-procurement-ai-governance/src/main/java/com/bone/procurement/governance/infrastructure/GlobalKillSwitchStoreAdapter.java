package com.bone.procurement.governance.infrastructure;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.GlobalKillSwitchStore;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.model.capability.GlobalKillSwitch;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 全域总闸持久化适配器。
 *
 * <p>{@link #current()} 每次调用都读最新持久化行，无任何缓存；写操作必须取登录态操作人， 缺失即 fail-closed 拒绝落库。写操作只在本 Adapter 内调用
 * SDK repo。
 */
@Component
@ConditionalOnAiGovernanceEnabled
public class GlobalKillSwitchStoreAdapter implements GlobalKillSwitchStore {

  private static final String SWITCH_KEY = "GLOBAL";

  private final GlobalKillSwitchSdkRepository repository;
  private final GovernanceActorPort actorPort;

  public GlobalKillSwitchStoreAdapter(
      GlobalKillSwitchSdkRepository repository, GovernanceActorPort actorPort) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.actorPort = Objects.requireNonNull(actorPort, "actorPort");
  }

  @Override
  public void save(GlobalKillSwitch killSwitch) {
    String operator = actorPort.currentActorId();
    if (operator == null || operator.isBlank()) {
      throw new IllegalStateException("总闸写入必须记录登录态操作人，拒绝匿名/自报");
    }
    String tenantId = GovernanceTenant.current();
    List<GlobalKillSwitchEntity> existing =
        repository.findByCriteria(
            Criteria.<GlobalKillSwitchEntity>create()
                .entityClass(GlobalKillSwitchEntity.class)
                .eq("tenantId", tenantId)
                .eq("switchKey", SWITCH_KEY));
    GlobalKillSwitchEntity entity = new GlobalKillSwitchEntity();
    if (!existing.isEmpty()) {
      entity.setId(existing.get(0).getId());
      entity.setVersion(existing.get(0).getVersion());
    } else {
      entity.setVersion(0);
    }
    entity.setTenantId(tenantId);
    entity.setSwitchKey(SWITCH_KEY);
    entity.setMode(killSwitch.mode().name());
    entity.setOperator(killSwitch.operator());
    entity.setReason(killSwitch.reason());
    entity.setChangedAt(Date.from(killSwitch.changedAt()));
    entity.setDrillRef(killSwitch.drillRef());
    Date now = new Date();
    entity.setCreatedAt(existing.isEmpty() ? now : existing.get(0).getCreatedAt());
    entity.setUpdatedAt(now);
    repository.save(entity);
  }

  @Override
  public GlobalKillSwitch current() {
    // 无缓存：每次裁决都重新查询最新持久化行。
    return repository
        .findByCriteria(
            Criteria.<GlobalKillSwitchEntity>create()
                .entityClass(GlobalKillSwitchEntity.class)
                .eq("tenantId", GovernanceTenant.current())
                .eq("switchKey", SWITCH_KEY))
        .stream()
        .max(Comparator.comparing(e -> e.getChangedAt().toInstant()))
        .map(
            e ->
                GlobalKillSwitch.rehydrate(
                    GlobalKillSwitch.Mode.valueOf(e.getMode()),
                    e.getOperator(),
                    e.getReason(),
                    e.getChangedAt().toInstant(),
                    e.getDrillRef()))
        .orElse(null);
  }
}
