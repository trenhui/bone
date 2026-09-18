package com.bone.blueprint.domain.shared.exception;

import com.bone.core.exception.DomainException;

/**
 * 乐观锁冲突异常（并发更新同一聚合根时 version 不匹配）。
 *
 * <p>多请求并发修改同一聚合根时，后来者的 {@code WHERE version = ?} 条件命中 0 行—— 数据库静默跳过更新，但业务层必须显式感知并上抛，由调用方决定重试或告警。
 *
 * <p>属 domain 层并发不变量异常，由 {@code *Repository#saveWithVersionCheck} 抛出， 应用层 catch 后按场景处理（UI 提示重试 /
 * 日志告警 / 事件驱动补偿）。
 */
public class OptimisticLockConflictException extends DomainException {

  private final long entityId;
  private final String entityType;
  private final long expectedVersion;

  public OptimisticLockConflictException(String entityType, long entityId, long expectedVersion) {
    super(
        "乐观锁冲突："
            + entityType
            + "["
            + entityId
            + "] 已被其他事务修改（期望 version="
            + expectedVersion
            + "），请重试或稍后再试");
    this.entityType = entityType;
    this.entityId = entityId;
    this.expectedVersion = expectedVersion;
  }

  public long getEntityId() {
    return entityId;
  }

  public String getEntityType() {
    return entityType;
  }

  public long getExpectedVersion() {
    return expectedVersion;
  }
}
