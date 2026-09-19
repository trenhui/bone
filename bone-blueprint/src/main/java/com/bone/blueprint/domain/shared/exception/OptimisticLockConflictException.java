package com.bone.blueprint.domain.shared.exception;

import com.bone.core.exception.BizException;

/**
 * 乐观锁冲突异常（并发更新同一聚合根时 version 不匹配）。
 *
 * <p>多请求并发修改同一聚合根时，后来者的 {@code WHERE version = ?} 条件命中 0 行—— 数据库静默跳过更新，但业务层必须显式感知并上抛，由调用方决定重试或告警。
 *
 * <p>属 domain 层并发不变量异常；D2 起由应用层捕获 SDK {@code OptimisticLockingFailureException} 后翻译抛出 （原由 {@code
 * *Repository#saveWithVersionCheck} 抛出），应用层 catch 后按场景处理（UI 提示重试 / 日志告警 / 事件驱动补偿）。
 *
 * <p><b>HTTP 映射</b>：继承 {@link BizException} 并自带 {@code 409}，由 {@code GlobalExceptionHandler} 的
 * {@code bizExceptionHandler} 直接映射为 HTTP 409（可重试的并发冲突，不是 5xx 服务端故障——若退化为 {@code DomainException}
 * 兜底会报成 500，污染 5xx 告警与 SLO 口径）。D2 前该类继承 {@code DomainException}，存在此缺口。
 */
public class OptimisticLockConflictException extends BizException {

  /** 并发冲突 → HTTP 409 CONFLICT。 */
  private static final int CONFLICT_HTTP_STATUS = 409;

  private final long entityId;
  private final String entityType;
  private final long expectedVersion;

  public OptimisticLockConflictException(String entityType, long entityId, long expectedVersion) {
    super(
        CONFLICT_HTTP_STATUS,
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
