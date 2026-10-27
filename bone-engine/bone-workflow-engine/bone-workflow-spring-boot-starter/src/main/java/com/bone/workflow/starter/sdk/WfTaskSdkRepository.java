package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 审批任务（{@code wf_task}）的 SDK {@code @Sql} 仓储（按表一个接口，与 {@link WfTaskRow} 一一对应）。
 *
 * <p>SQL 与并发口径随方法文档沉淀在此（2026-10-24 自裸 JDBC 收敛，HC-006 豁免随之移除）；H2 测试 （{@code
 * wf-schema-h2.sql}）原样执行这些语句，避免「测试另写一份 SQL → 测试绿、生产死锁」。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词—— SDK 的
 * {@code @Sql} 通道不经过租户过滤器，漏谓词即多租户串数据，且单租户单测完全看不出来。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfTaskSdkRepository extends Repository<WfTaskRow, String> {

  /**
   * 新建任务（审计列 {@code created_by}/{@code updated_by} 均为 {@code operatorId}）。
   *
   * <p><b>{@code node_round} 由实例行现取</b>（标量子查询），不由调用方传入：轮次是实例的状态，任务只是它的从属；
   * 让引擎把轮次一路透传下来，任何一处漏传都会造出「轮次不对」的任务——它会永远不参与完成判定，表现成「有人一直没表态」。
   */
  @Sql(
      "INSERT INTO wf_task (id, tenant_id, instance_id, node_code, assignee_id, status, decision,"
          + " comment, due_at, node_round, created_at, updated_at, created_by, updated_by,"
          + " deleted, version)"
          + " VALUES (#{id}, #{tenantId}, #{instanceId}, #{nodeCode}, #{assigneeId}, #{status},"
          + " #{decision}, #{comment}, #{dueAt},"
          + " (SELECT node_round FROM wf_instance"
          + "   WHERE id = #{instanceId} AND tenant_id = #{tenantId}),"
          + " #{now}, #{now}, #{operatorId}, #{operatorId}, 0, 0)")
  int insertTask(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("instanceId") String instanceId,
      @Param("nodeCode") String nodeCode,
      @Param("assigneeId") String assigneeId,
      @Param("status") String status,
      @Param("decision") String decision,
      @Param("comment") String comment,
      @Param("dueAt") Instant dueAt,
      @Param("now") Instant now,
      @Param("operatorId") String operatorId);

  /**
   * <b>条件插入</b>任务：仅当同一 {@code (instanceId, nodeCode, assigneeId)} 下没有 PENDING 任务时才插入。
   *
   * <p><b>为什么不能「先查后插」</b>：管理员强制指派（{@code assignTask}）是可重试操作，两个管理员（或用户连点） 并发指派时「先查无待办再插」会双插——同人同节点两条
   * PENDING。会签（ALL）节点下多余的那条让节点 <b>永远等不齐</b>。判据由数据库在同一条语句里依据最新行状态计算（与 {@link
   * WfInstanceSdkRepository#compareAndAdvanceNode} 同纪律）。
   */
  @Sql(
      "INSERT INTO wf_task (id, tenant_id, instance_id, node_code, assignee_id, status, decision,"
          + " comment, due_at, node_round, created_at, updated_at, created_by, updated_by,"
          + " deleted, version)"
          + " SELECT #{id}, #{tenantId}, #{instanceId}, #{nodeCode}, #{assigneeId}, #{status},"
          + " #{decision}, #{comment}, #{dueAt},"
          + " (SELECT node_round FROM wf_instance"
          + "   WHERE id = #{instanceId} AND tenant_id = #{tenantId}),"
          + " #{now}, #{now}, #{operatorId}, #{operatorId}, 0, 0"
          + " FROM (SELECT 1) AS insert_guard"
          + " WHERE NOT EXISTS (SELECT 1 FROM wf_task t"
          + "     WHERE t.tenant_id = #{tenantId} AND t.instance_id = #{instanceId}"
          + "       AND t.node_code = #{nodeCode} AND t.assignee_id = #{assigneeId}"
          + "       AND t.status = 'PENDING' AND t.deleted = 0)")
  int insertTaskIfAbsent(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("instanceId") String instanceId,
      @Param("nodeCode") String nodeCode,
      @Param("assigneeId") String assigneeId,
      @Param("status") String status,
      @Param("decision") String decision,
      @Param("comment") String comment,
      @Param("dueAt") Instant dueAt,
      @Param("now") Instant now,
      @Param("operatorId") String operatorId);

  @Sql(
      "SELECT id, instance_id, node_code, assignee_id, status, decision, comment, due_at, created_at,"
          + " decided_at FROM wf_task WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  Optional<WfTaskRow> findTaskRow(@Param("id") String id, @Param("tenantId") Long tenantId);

  @Sql(
      "SELECT id, instance_id, node_code, assignee_id, status, decision, comment, due_at, created_at,"
          + " decided_at FROM wf_task"
          + " WHERE instance_id = #{instanceId} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND status = 'PENDING' ORDER BY id")
  List<WfTaskRow> listPendingTaskRows(
      @Param("instanceId") String instanceId, @Param("tenantId") Long tenantId);

  @Sql(
      "SELECT id, instance_id, node_code, assignee_id, status, decision, comment, due_at, created_at,"
          + " decided_at FROM wf_task"
          + " WHERE instance_id = #{instanceId} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND node_code = #{nodeCode} ORDER BY id")
  List<WfTaskRow> listTaskRowsByNode(
      @Param("instanceId") String instanceId,
      @Param("tenantId") Long tenantId,
      @Param("nodeCode") String nodeCode);

  /**
   * 取某实例某节点<b>当前轮次</b>的任务（会签完成判定的应用层复核）。
   *
   * <p>轮次取自实例行（{@code wf_instance.node_round}），由 {@code compareAndAdvanceNode} 每次推进时自增。
   * 不看轮次就会把驳回退回前那批已通过的任务算进本轮——比例会签（RATIO）的「已通过 + 待决 &lt; 法定」判定会因此算错， 该终止的不终止、该继续的被终止，两种情况都是静默的。
   */
  @Sql(
      "SELECT t.id, t.instance_id, t.node_code, t.assignee_id, t.status, t.decision, t.comment,"
          + " t.due_at, t.created_at, t.decided_at FROM wf_task t"
          + " JOIN wf_instance i ON i.id = t.instance_id AND i.tenant_id = t.tenant_id"
          + " WHERE t.instance_id = #{instanceId} AND t.tenant_id = #{tenantId}"
          + "   AND t.node_code = #{nodeCode} AND t.node_round = i.node_round"
          + "   AND t.deleted = 0 ORDER BY t.id")
  List<WfTaskRow> listTaskRowsByNodeCurrentRound(
      @Param("instanceId") String instanceId,
      @Param("tenantId") Long tenantId,
      @Param("nodeCode") String nodeCode);

  @Sql(
      "SELECT id, instance_id, node_code, assignee_id, status, decision, comment, due_at, created_at,"
          + " decided_at FROM wf_task"
          + " WHERE assignee_id = #{assigneeId} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND status = #{status} ORDER BY id")
  List<WfTaskRow> listTaskRowsByAssignee(
      @Param("assigneeId") String assigneeId,
      @Param("tenantId") Long tenantId,
      @Param("status") String status);

  /** 按实例取全部任务（历史轨迹）；排序按 {@code created_at, id}（UUID 宿主下 id 字典序≠时间序）。 */
  @Sql(
      "SELECT id, instance_id, node_code, assignee_id, status, decision, comment, due_at, created_at,"
          + " decided_at FROM wf_task"
          + " WHERE instance_id = #{instanceId} AND tenant_id = #{tenantId} AND deleted = 0"
          + " ORDER BY created_at, id")
  List<WfTaskRow> listTaskRows(
      @Param("instanceId") String instanceId, @Param("tenantId") Long tenantId);

  /**
   * 任务状态 CAS：仅当当前状态为 {@code expected} 且所属实例仍在 RUNNING 时才改。
   *
   * <p><b>实例终态守卫</b>（{@code EXISTS ... i.phase = 'RUNNING'}）：流程已终态后迟到的决策不得再生效——
   * 否则「管理员已强制终止、审批人随后点同意」会把状态写花。两个审批人同时点时，仅一个 CAS 成功。
   */
  @Sql(
      "UPDATE wf_task SET status = #{next}, decision = #{decision}, comment = #{comment},"
          + " decided_at = #{decidedAt}, updated_at = #{updatedAt}, updated_by = #{operatorId},"
          + " version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND status = #{expected}"
          + "   AND EXISTS (SELECT 1 FROM wf_instance i"
          + "       WHERE i.id = wf_task.instance_id AND i.tenant_id = wf_task.tenant_id"
          + "         AND i.deleted = 0 AND i.phase = 'RUNNING')")
  int compareAndSetTaskStatus(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("expected") String expected,
      @Param("next") String next,
      @Param("decision") String decision,
      @Param("comment") String comment,
      @Param("decidedAt") Instant decidedAt,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /** 改派：仅 PENDING 任务可改派（已决策的任务办理权不可转移）。 */
  @Sql(
      "UPDATE wf_task SET assignee_id = #{newAssigneeId}, updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 'PENDING' AND deleted = 0")
  int reassignTask(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("newAssigneeId") String newAssigneeId,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /** 作废实例全部待办（撤回 / 终止时）。 */
  @Sql(
      "UPDATE wf_task SET status = 'CANCELLED', updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE instance_id = #{instanceId} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND status = 'PENDING'")
  int cancelPendingTasks(
      @Param("instanceId") String instanceId,
      @Param("tenantId") Long tenantId,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /** 作废实例某节点的待办（节点推进后清理遗留）。 */
  @Sql(
      "UPDATE wf_task SET status = 'CANCELLED', updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE instance_id = #{instanceId} AND tenant_id = #{tenantId} AND deleted = 0"
          + "   AND node_code = #{nodeCode} AND status = 'PENDING'")
  int cancelPendingTasksOfNode(
      @Param("instanceId") String instanceId,
      @Param("tenantId") Long tenantId,
      @Param("nodeCode") String nodeCode,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);
}
