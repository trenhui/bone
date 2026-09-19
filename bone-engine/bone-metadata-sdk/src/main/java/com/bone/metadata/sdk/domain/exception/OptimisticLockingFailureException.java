package com.bone.metadata.sdk.domain.exception;

/**
 * 乐观锁冲突异常：并发更新下期望的 {@code version} 与库内不一致（或行不存在 / 已不在当前租户）。
 *
 * <p>SDK 原生乐观锁（ADR-0031 D1）：当实体标注 {@code @Version} 时，{@code save}/{@link
 * com.bone.metadata.sdk.Repository#update(Object)} 会生成 {@code SET version = version + 1} 且 WHERE 携带
 * {@code version = :old}，影响行数为 0 即抛出本异常——MySQL 无法区分「版本冲突」与「行不存在/租户不匹配」，统一按冲突处理 （与 D2 前 blueprint 手写
 * {@code *Repository#saveWithVersionCheck} 语义一致）。
 *
 * <p>调用方应捕获后按场景处理（UI 提示重试 / 幂等丢弃 / 告警），不应静默忽略。
 */
public class OptimisticLockingFailureException extends SDKException {

  public OptimisticLockingFailureException(String entityClass, Object id, Object expectedVersion) {
    super(
        "OPTIMISTIC_LOCK_CONFLICT",
        String.format(
            "Optimistic locking conflict on %s id=%s expectedVersion=%s: row was concurrently "
                + "modified, deleted, or not visible under current tenant. Update was rolled back.",
            entityClass, id, expectedVersion),
        null);
  }
}
