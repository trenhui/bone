package com.bone.metadata.runtime.application;

import java.util.Optional;

/**
 * 动作执行日志（幂等去重 + RC-009 记录级变更审计）。
 *
 * <p>资金类动作（{@code requires_idempotency=1}）以 {@code (tenant, entity, record, action,
 * idempotencyKey)} 唯一约束去重：同一幂等键重复提交直接返回首次结果，绝不二次执行副作用。
 */
public interface RuntimeActionExecutionRepository {

  /** 按幂等键查已成功的执行结果 JSON（资金类动作防重放）。无则返回空。 */
  Optional<String> findSuccessResultByIdempotency(
      long tenantId, String entityCode, String recordId, String actionCode, String idempotencyKey);

  /** 落一条执行记录（status=1 成功 / 2 失败）。 */
  void record(
      long tenantId,
      String entityCode,
      String recordId,
      String actionCode,
      String idempotencyKey,
      boolean success,
      String resultJson,
      String operatorId);
}
