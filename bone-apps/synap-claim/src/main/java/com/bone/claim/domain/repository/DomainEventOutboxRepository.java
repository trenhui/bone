package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.sql.Timestamp;
import java.util.List;

/**
 * 领域事件发件箱仓储（ADR-008）。
 *
 * <p>扫描入口 {@link #findPendingOutboxAllTenants} 是全租户方法（{@code @TenantScope(ALL)}），仅授权给 {@code
 * adapter.schedule} 的 {@code OutboxRelayJob} 调用（ADR-0030 守卫：调度包只许调 {@code *AllTenants}
 * 仓储方法）。其余读/写（{@code load} / {@code save}）均在应用层 {@code OutboxApplicationService} 内、租户上下文下发生。
 */
public interface DomainEventOutboxRepository extends Repository<DomainEventOutbox, Long> {

  /** 跨租户扫描待投发件箱记录（调度专用）。无租户过滤锚点——全租户扫描须显式携带租户进派发动作。 */
  @TenantScope(TenantScopeMode.ALL)
  List<DomainEventOutbox> findPendingOutboxAllTenants(
      @Param("before") Timestamp before, @Param("limit") int limit);

  /** 按主键加载（应用层在 {@code runAs} 上下文内调用，租户隔离由上下文 + AUTO 作用域落实）。 */
  default DomainEventOutbox load(Long tenantId, Long id) {
    return findById(id);
  }
}
