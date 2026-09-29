package com.bone.platform.alert.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.platform.alert.domain.model.notification.NotificationMessage;
import java.util.List;

/** 站内信仓储（由使用方通过 @EnableSqlRepositories 自动实现）。 */
public interface NotificationMessageRepository extends Repository<NotificationMessage, Long> {

  /** 按用户查询站内信（读侧 DSL 收敛到仓储，应用层不直接依赖，ADR-0030）。 */
  default List<NotificationMessage> findByUserId(Long userId) {
    return QueryBuilder.from(NotificationMessage.class)
        .where(NotificationMessage::getUserId)
        .eq(userId)
        .list();
  }

  /**
   * 租户圈定的按 ID 查询：ID + tenantId 双条件，保证跨租户 ID 即便被猜中也取不到记录（IDOR 第一道闸）。
   *
   * <p>命中多条不可能（ID 唯一），取首条仅为满足返回类型。
   */
  default NotificationMessage findByIdAndTenant(Long id, Long tenantId) {
    List<NotificationMessage> list =
        QueryBuilder.from(NotificationMessage.class)
            .where(NotificationMessage::getId)
            .eq(id)
            .where(NotificationMessage::getTenantId)
            .eq(tenantId)
            .list();
    return list.isEmpty() ? null : list.get(0);
  }

  /** 该用户未读站内信数。SDK 的 count 多条件组合存在异常，改为在仓储层拉取后过滤（数据量小）。 */
  default long countUnreadByUserId(Long userId) {
    return findByUserId(userId).stream().filter(m -> !m.isRead()).count();
  }
}
