package com.bone.metadata.runtime.application;

import java.util.List;
import java.util.Map;

/**
 * 动作执行日志（幂等去重 + RC-009 记录级变更审计 + P0-2 半事务防护）。
 *
 * <p>资金类动作（{@code requires_idempotency=1}）以 {@code (tenant, entity, record, action,
 * idempotencyKey)} 唯一约束去重。执行生命周期：<br>
 * {@code 3=PENDING（begin，副作用执行前落行）} → {@code 1=SUCCEEDED / 2=FAILED / 4=ORPHANED（副作用已发出、状态流转未完成）}。
 *
 * <p><b>为什么 begin 必须先于副作用</b>：旧时序「副作用 → 状态流转 → 成功才写日志」存在半事务窗口—— 网关已受理、平台 {@code updateState}
 * 失败时零留痕，重试即可能二次出金（Runtime §4.8.1）。 先落 PENDING 行使任何一环崩溃（含 JVM kill、DB 闪断）后，系统里都有一行可对账记录。
 * 并发重放由幂等唯一键兜底：并发双方同时 begin 时落败方收到 {@code DuplicateKeyException}，客户端重试后走幂等命中路径收敛。
 */
public interface RuntimeActionExecutionRepository {

  /** 执行状态：1=成功。 */
  long STATUS_SUCCEEDED = 1L;

  /** 执行状态：2=失败（副作用前/执行中失败，记录错误摘要）。 */
  long STATUS_FAILED = 2L;

  /** 执行状态：3=PENDING（begin 落行，副作用执行前）。 */
  long STATUS_PENDING = 3L;

  /** 执行状态：4=孤儿（副作用已发出、状态流转未完成；须对账/人工推进，禁止自动重放）。 */
  long STATUS_ORPHANED = 4L;

  /**
   * 按幂等键查最近一次执行（任意状态：成功 / 失败 / PENDING / 孤儿）。
   *
   * @return 行视图 {@code status}（Number）与 {@code result_json}（String，可能 null）；无记录返回 {@code null}
   */
  Map<String, Object> findByIdempotency(
      long tenantId, String entityCode, String recordId, String actionCode, String idempotencyKey);

  /**
   * 查某记录的<b>全部</b>动作执行历史（按时间正序，供记录详情的时间线 UI）。
   *
   * <p>为什么需要：执行日志此前只服务幂等去重（按 idempotencyKey 点查），没有任何按记录回放的入口——
   * 而「这张单据什么时候被谁提交/审批/打款、每次结果如何」恰是审计时间线的刚需 （等保/SOX 场景）。数据早已落库（P0-2 时序保证半事务也有行），缺的只是读路径。
   *
   * @return 行视图（id / action_code / status / result_json / operator_id / created_at）；无记录返回空列表
   */
  List<Map<String, Object>> findByRecord(long tenantId, String entityCode, String recordId);

  /**
   * 落一条 PENDING 执行记录（副作用执行<strong>之前</strong>调用，P0-2 核心时序）。
   *
   * @return 执行日志行 id（供 mark* 关闭生命周期）
   */
  long begin(
      long tenantId,
      String entityCode,
      String recordId,
      String actionCode,
      String idempotencyKey,
      String operatorId,
      Map<String, Object> params);

  /** 终态：副作用执行且状态流转均成功（幂等复用其 resultJson）。 */
  void markSucceeded(long executionId, String resultJson);

  /** 终态：异常结束（副作用前失败或副作用失败）——错误摘要入 result_json，绝不误标为成功。 */
  void markFailed(long executionId, String errorSummary);

  /** 终态：副作用已发出但状态流转失败（「已扣款但单据未流转」孤儿，须对账/人工推进，禁止自动重放）。 */
  void markOrphaned(long executionId, String note);
}
