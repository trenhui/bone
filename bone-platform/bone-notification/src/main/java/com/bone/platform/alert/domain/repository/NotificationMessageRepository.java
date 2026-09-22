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

  /** 该用户未读站内信数。SDK 的 count 多条件组合存在异常，改为在仓储层拉取后过滤（数据量小）。 */
  default long countUnreadByUserId(Long userId) {
    return findByUserId(userId).stream().filter(m -> !m.isRead()).count();
  }
}
