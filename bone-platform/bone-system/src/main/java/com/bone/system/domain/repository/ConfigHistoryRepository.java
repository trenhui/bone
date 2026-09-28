package com.bone.system.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.config.ConfigHistory;
import java.util.List;

/**
 * 配置变更历史仓储端口（读侧）。写由 {@code ConfigHistoryProjector}（AFTER_COMMIT 订阅器）在 {@code ConfigChangedEvent} /
 * {@code ConfigCreatedEvent} 提交后投影落库，不占用配置主写事务（R9）， 不另立 Handler（E-3.2 Ceremonial Architecture 评判）。
 */
public interface ConfigHistoryRepository extends Repository<ConfigHistory, Long> {

  /** 按配置 ID 取变更历史，按变更时间倒序（最新在前）。 */
  default List<ConfigHistory> findByConfigIdOrdered(Long configId) {
    return QueryBuilder.from(ConfigHistory.class)
        .where(ConfigHistory::getConfigId)
        .eq(configId)
        .orderByDesc(ConfigHistory::getCreatedAt)
        .list();
  }

  /** 按事件 ID 计数（R7：幂等判重用，事件重放前确认是否已落库）。 */
  default Long countByEventId(String eventId) {
    return countByCriteria(
        Criteria.<ConfigHistory>create()
            .entityClass(ConfigHistory.class)
            .eq(ConfigHistory::getEventId, eventId));
  }
}
