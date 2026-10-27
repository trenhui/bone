package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.Optional;

/**
 * 流程实例（{@code wf_instance}）的 SDK {@code @Sql} 仓储。
 *
 * <p>SQL 与并发口径随方法文档沉淀在此（2026-10-24 自裸 JDBC 收敛，HC-006 豁免随之移除）；H2 测试 （{@code
 * wf-schema-h2.sql}）原样执行这些语句，避免「测试另写一份 SQL → 测试绿、生产死锁」。 审批任务（{@code wf_task}）的语句在 {@link
 * WfTaskSdkRepository}——按表一个接口，与行类一一对应。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词—— SDK 的
 * {@code @Sql} 通道不经过租户过滤器，漏谓词即多租户串数据，且单租户单测完全看不出来。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfInstanceSdkRepository extends Repository<WfInstanceRow, String> {

  /**
   * 新建实例行。
   *
   * <p>{@code created_at} 用 {@code now}、{@code updated_at} 用调用方传入的 {@code updatedAt}（与领域实例的 {@code
   * updatedAt} 对齐）；审计列 {@code created_by}/{@code updated_by} 均由 {@code operatorId} 显式提供
   * （端口纪律：审计列回答「谁」，不得用租户 id 冒充）。
   */
  @Sql(
      "INSERT INTO wf_instance (id, tenant_id, entity_code, record_id, def_key, def_version,"
          + " current_node_code, phase, started_by, started_at, updated_at, created_by, created_at,"
          + " updated_by, deleted, version)"
          + " VALUES (#{id}, #{tenantId}, #{entityCode}, #{recordId}, #{defKey}, #{defVersion},"
          + " #{currentNodeCode}, #{phase}, #{startedBy}, #{startedAt}, #{updatedAt}, #{operatorId},"
          + " #{now}, #{operatorId}, 0, 0)")
  int insertInstance(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("defKey") String defKey,
      @Param("defVersion") int defVersion,
      @Param("currentNodeCode") String currentNodeCode,
      @Param("phase") String phase,
      @Param("startedBy") String startedBy,
      @Param("startedAt") Instant startedAt,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId,
      @Param("now") Instant now);

  /** 更新实例行（含 phase / 节点推进后的落库）；{@code version = version + 1} 供乐观锁检测同行丢失更新。 */
  @Sql(
      "UPDATE wf_instance SET entity_code = #{entityCode}, record_id = #{recordId}, def_key = #{defKey},"
          + " def_version = #{defVersion}, current_node_code = #{currentNodeCode}, phase = #{phase},"
          + " started_by = #{startedBy}, started_at = #{startedAt}, updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId}")
  int updateInstance(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("defKey") String defKey,
      @Param("defVersion") int defVersion,
      @Param("currentNodeCode") String currentNodeCode,
      @Param("phase") String phase,
      @Param("startedBy") String startedBy,
      @Param("startedAt") Instant startedAt,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  @Sql(
      "SELECT id, entity_code, record_id, def_key, def_version, current_node_code, phase, started_by,"
          + " started_at, updated_at FROM wf_instance"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  Optional<WfInstanceRow> findInstanceRow(@Param("id") String id, @Param("tenantId") Long tenantId);

  /**
   * 按业务单据取<b>最新</b>实例（驳回/撤回后重提是新实例，历史按单据可累积）。
   *
   * <p>排序按<b>时间列</b>（{@code started_at DESC, id DESC}）：id 在 UUID 宿主下字典序≠时间序，按 id 决胜会取错实例。
   */
  @Sql(
      "SELECT id, entity_code, record_id, def_key, def_version, current_node_code, phase, started_by,"
          + " started_at, updated_at FROM wf_instance"
          + " WHERE entity_code = #{entityCode} AND record_id = #{recordId} AND tenant_id = #{tenantId}"
          + " AND deleted = 0 ORDER BY started_at DESC, id DESC LIMIT 1")
  Optional<WfInstanceRow> findLatestInstanceRowByRecord(
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("tenantId") Long tenantId);

  /**
   * 实例相位 CAS：仅当当前 phase 仍为 {@code expected} 时才改。
   *
   * <p><b>只 guard phase，不 guard version</b>：端口签名无 expectedVersion；version 列 +1 仅作同行丢失更新检测， 不作第二
   * guard（设计文档 §4.2 决策 3）。终态 phase CAS 由调用方（适配器）同事务释放活动指针。
   */
  @Sql(
      "UPDATE wf_instance SET phase = #{next}, updated_at = #{updatedAt}, updated_by = #{operatorId},"
          + " version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND phase = #{expected}")
  int compareAndSetPhase(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("expected") String expected,
      @Param("next") String next,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /**
   * 节点级原子推进（会签场景的关键并发点）：完成条件的判定与推进在同一语句内依据最新行状态计算。
   *
   * <p>完成条件口径（{@code mode}）：{@code AUTO} 无条件移动（START / CONDITION / CC 等非审批节点）； {@code ALL} 会签 =
   * 全部任务落在已决策显式集（{@code APPROVED/REJECTED/CANCELLED}，不用「无 PENDING」近似） 且无人驳回；{@code ANY} 或签 = 至少 1 人
   * APPROVED 且无人驳回；{@code SINGLE} 单签 = 至少 1 人 APPROVED（驳回由 completeTask 兜底整单终止， 此处不校验
   * REJECTED，否则「驳回退回重入」时历史访问的陈旧 REJECTED 会误阻断本次推进）。
   *
   * <p><b>REJECTED 守卫的适用范围</b>：仅 {@code ALL}/{@code ANY} 多办理人同访问需要——任一驳回即整单驳回； {@code
   * SINGLE}/{@code AUTO} 不挂此守卫（单签驳回由引擎兜底，非审批节点本就无驳回）。
   *
   * <p><b>为什么不得拆成先查后改</b>：两个会签人同时点「同意」时各自读到「对方还没通过」的旧快照 ⇒ 双方都判「未齐」 ⇒
   * 流程永远卡在待办（会签经典死锁）。判据必须由数据库在同一条语句里算。
   */
  @Sql(
      "UPDATE wf_instance SET current_node_code = #{nextNodeCode}, updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE id = #{instanceId} AND tenant_id = #{tenantId}"
          + "   AND current_node_code = #{nodeCode}"
          + "   AND phase = 'RUNNING'"
          + "   AND (#{mode} NOT IN ('ALL', 'ANY')"
          + "     OR NOT EXISTS (SELECT 1 FROM wf_task t"
          + "         WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "           AND t.node_code = #{nodeCode} AND t.deleted = 0 AND t.status = 'REJECTED'))"
          + "   AND ("
          + "     #{mode} = 'AUTO'"
          + "     OR (#{mode} = 'ALL'"
          + "       AND EXISTS (SELECT 1 FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = #{nodeCode} AND t.deleted = 0)"
          + "       AND NOT EXISTS (SELECT 1 FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = #{nodeCode} AND t.deleted = 0"
          + "             AND t.status NOT IN ('APPROVED', 'REJECTED', 'CANCELLED')))"
          + "     OR (#{mode} <> 'ALL' AND EXISTS (SELECT 1 FROM wf_task t"
          + "         WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "           AND t.node_code = #{nodeCode} AND t.deleted = 0 AND t.status = 'APPROVED'))"
          + "   )")
  int compareAndAdvanceNode(
      @Param("instanceId") String instanceId,
      @Param("tenantId") Long tenantId,
      @Param("nodeCode") String nodeCode,
      @Param("nextNodeCode") String nextNodeCode,
      @Param("mode") String mode,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);
}
