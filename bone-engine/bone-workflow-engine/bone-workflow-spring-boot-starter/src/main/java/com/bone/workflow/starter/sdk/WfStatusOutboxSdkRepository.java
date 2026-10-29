package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.List;

/**
 * 状态投影发件箱（{@code wf_status_outbox}）的 SDK {@code @Sql} 仓储。
 *
 * <p><b>跨租户口径</b>：{@link #pollDueStatusIntents} / {@link #findTerminalMissingProjection} / {@link
 * #purgeDoneStatusIntents} 三条跑在无 TenantContext 的调度线程，<b>刻意不按租户过滤</b>——租户由行内 {@code tenant_id}
 * 携带，由中继逐条重建上下文。若在此按单租户过滤，无上下文线程要么抛错要么只投影一个租户。 写路径（insert / claim / markDone /
 * markFailed）由适配器显式传租户（{@code MANUAL} + 显式谓词）。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfStatusOutboxSdkRepository extends Repository<WfStatusOutboxRow, String> {

  @Sql(
      "INSERT INTO wf_status_outbox (id, tenant_id, instance_id, entity_code, record_id,"
          + " operator_id, intent, action_code, params_json, state, retry_count, next_retry_at,"
          + " created_at, updated_at)"
          + " VALUES (#{id}, #{tenantId}, #{instanceId}, #{entityCode}, #{recordId},"
          + " #{operatorId}, #{intent}, #{actionCode}, #{paramsJson}, 'NEW', 0, NULL, #{now}, #{now})")
  int insertStatusIntent(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("instanceId") String instanceId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("operatorId") String operatorId,
      @Param("intent") String intent,
      @Param("actionCode") String actionCode,
      @Param("paramsJson") String paramsJson,
      @Param("now") Instant now);

  /**
   * 原子抢占：把 NEW / 到期的 FAILED / 超时的 CLAIMED 改成 CLAIMED，返回 1 = 抢到。
   *
   * <p>多副本同时 poll 同一条时仅一个副本能抢到——杜绝「双副本重复执行业务动作」。
   */
  @Sql(
      "UPDATE wf_status_outbox SET state = 'CLAIMED', updated_at = #{now}"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND (state = 'NEW'"
          + "        OR (state = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))"
          + "        OR (state = 'CLAIMED' AND updated_at <= #{staleCutoff}))")
  int claimStatusIntent(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("now") Instant now,
      @Param("staleCutoff") Instant staleCutoff);

  /**
   * 取到期意图，按 {@code created_at} 升序 + <b>队头守卫</b>（同实例有更早在途意图时不返回）。
   *
   * <p>不按租户过滤（调度线程无上下文，租户由行内携带）。
   */
  @Sql(
      "SELECT id, tenant_id, instance_id, entity_code, record_id, operator_id, intent, action_code,"
          + " params_json, state, retry_count, next_retry_at, created_at"
          + " FROM wf_status_outbox"
          + " WHERE deleted = 0"
          + "   AND (state = 'NEW'"
          + "        OR (state = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))"
          + "        OR (state = 'CLAIMED' AND updated_at <= #{staleCutoff}))"
          + "   AND NOT EXISTS (SELECT 1 FROM wf_status_outbox earlier"
          + "       WHERE earlier.tenant_id = wf_status_outbox.tenant_id"
          + "         AND earlier.instance_id = wf_status_outbox.instance_id"
          + "         AND earlier.deleted = 0"
          + "         AND earlier.state IN ('NEW', 'CLAIMED')"
          + "         AND (earlier.created_at < wf_status_outbox.created_at"
          + "              OR (earlier.created_at = wf_status_outbox.created_at"
          + "                  AND earlier.id < wf_status_outbox.id)))"
          + " ORDER BY created_at, id LIMIT #{limit}")
  List<WfStatusOutboxRow> pollDueStatusIntents(
      @Param("now") Instant now,
      @Param("staleCutoff") Instant staleCutoff,
      @Param("limit") int limit);

  @Sql(
      "UPDATE wf_status_outbox SET state = 'DONE', updated_at = #{now}"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  int markStatusIntentDone(
      @Param("id") String id, @Param("tenantId") Long tenantId, @Param("now") Instant now);

  /** 标记失败并排定下次重试（指数退避）；{@code retry_count < maxRetries} 条件保证有上限。 */
  @Sql(
      "UPDATE wf_status_outbox SET state = 'FAILED', retry_count = retry_count + 1,"
          + " next_retry_at = #{nextRetryAt}, updated_at = #{now}"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND state IN ('FAILED', 'CLAIMED') AND retry_count < #{maxRetries}")
  int markStatusIntentFailed(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("nextRetryAt") Instant nextRetryAt,
      @Param("maxRetries") int maxRetries,
      @Param("now") Instant now);

  /**
   * 终态失败（P0-2）：推理据「动作未声明幂等」等不可重试根因——落 FAILED 但把 {@code next_retry_at} 推到远未来， 使其永不在 {@code pollDue}
   * 的「FAILED 且到期」判据内被重新抢占执行（重投正是危险源）。停留在 FAILED 仅作人工可见的死信。
   */
  @Sql(
      "UPDATE wf_status_outbox SET state = 'FAILED', next_retry_at = #{never}, updated_at = #{now}"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND state IN ('NEW', 'CLAIMED', 'FAILED')")
  int markStatusIntentTerminallyFailed(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("never") Instant never,
      @Param("now") Instant now);

  /**
   * 对账：窗口内的终态实例里，<b>完全没有任何同 intent 的意图行</b>的（意图丢失，需补投）。
   *
   * <p>{@code i.phase AS intent}——把实例终态投影为意图语义，与写入侧 {@code OutboxStatusPort} 的 intent 逐字一致。
   * 不按租户过滤（对账作业跑在无上下文线程）。
   */
  @Sql(
      "SELECT i.id, i.tenant_id, i.entity_code, i.record_id, i.phase AS intent"
          + " FROM wf_instance i"
          + " WHERE i.deleted = 0"
          + "   AND i.phase IN ('APPROVED', 'REJECTED', 'WITHDRAWN', 'TERMINATED')"
          + "   AND i.updated_at >= #{horizon}"
          + "   AND NOT EXISTS (SELECT 1 FROM wf_status_outbox o"
          + "       WHERE o.tenant_id = i.tenant_id AND o.instance_id = i.id"
          + "         AND o.intent = i.phase)"
          + " LIMIT #{limit}")
  List<WfStatusOutboxRow> findTerminalMissingProjection(
      @Param("limit") int limit, @Param("horizon") Instant horizon);

  /** 按保留期物理清理 DONE 行（{@code deleted=1} 的软删行一并清掉）。跨租户，不按租户过滤。 */
  @Sql("DELETE FROM wf_status_outbox" + " WHERE state = 'DONE' AND updated_at < #{cutoff}")
  int purgeDoneStatusIntents(@Param("cutoff") Instant cutoff);
}
