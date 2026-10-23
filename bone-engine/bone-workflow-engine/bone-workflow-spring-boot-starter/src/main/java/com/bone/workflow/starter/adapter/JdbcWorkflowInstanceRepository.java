package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.starter.persistence.WfSql;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程实例 / 任务仓储的平台实现（JDBC → {@code wf_instance} / {@code wf_task}）。
 *
 * <p><b>持久化通道说明</b>：本类当前用 {@link NamedParameterJdbcTemplate} 直连，与同族 {@code
 * bone-metadata-engine-runtime} 的 {@code JdbcRuntimeRecordService} 同款形态。{@code
 * compareAndAdvanceNode} 要求「判据 + 推进」落在<b>同一条 SQL</b>——SDK 的 {@code @Sql} 通道可以表达该形态（护栏只禁 DDL / 多语句 /
 * 参数值内关键字，不禁子查询）， 故「SDK 无法表达」不成立；保留裸 JDBC 是<b>过渡态</b>，迁移路线与结论见设计文档《12. 工作流引擎设计》§10-B， 迁移完成后同步移除
 * {@code sdk-persistence-bypass-baseline} 中的对应豁免登记。
 *
 * <p><b>租户纪律</b>：引擎 API 不带 tenantId，故每条 SQL 都显式补 {@code tenant_id} 过滤，由 {@link TenantContext}
 * 提供；上下文缺失即失败关闭（不静默查全租户）。
 *
 * <p><b>审计归属纪律</b>：写审计列的 {@code operatorId} 一律取端口方法入参（引擎用例签名均持有操作人）， <b>不得</b>从 {@link
 * TenantContext} 取租户 id 冒充——审计列要回答「谁」，租户 id 回答「哪个租户」。
 */
public class JdbcWorkflowInstanceRepository implements WorkflowInstanceRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkflowInstanceRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  // ====================== 实例 ======================

  @Override
  @Transactional
  public boolean claimAndSaveInstance(WorkflowInstance instance) {
    if (!claimActiveInstance(instance)) {
      return false;
    }
    saveInstance(instance, instance.startedBy());
    return true;
  }

  @Override
  public void saveInstance(WorkflowInstance instance, String operatorId) {
    MapSqlParameterSource params = instanceParams(instance, operatorId);
    int updated = jdbc.update(WfSql.UPDATE_INSTANCE, params);
    if (updated == 0) {
      jdbc.update(WfSql.INSERT_INSTANCE, params);
    }
  }

  @Override
  public Optional<WorkflowInstance> findInstance(String instanceId) {
    List<WorkflowInstance> rows =
        jdbc.query(
            WfSql.SELECT_INSTANCE_BY_ID,
            params().addValue("id", instanceId),
            (rs, n) -> mapInstance(rs));
    return rows.stream().findFirst();
  }

  @Override
  public Optional<WorkflowInstance> findInstanceByRecord(String entityCode, String recordId) {
    List<WorkflowInstance> rows =
        jdbc.query(
            WfSql.SELECT_INSTANCE_BY_RECORD,
            params().addValue("entityCode", entityCode).addValue("recordId", recordId),
            (rs, n) -> mapInstance(rs));
    return rows.stream().findFirst();
  }

  @Override
  public boolean compareAndSetPhase(
      String instanceId,
      WorkflowInstance.Phase expected,
      WorkflowInstance.Phase next,
      String operatorId) {
    int updated =
        jdbc.update(
            WfSql.COMPARE_AND_SET_PHASE,
            params()
                .addValue("id", instanceId)
                .addValue("expected", expected.name())
                .addValue("next", next.name())
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("operatorId", operatorId));
    if (updated == 0) {
      return false;
    }
    // 终态即释放活动槽位：否则上一次流程结束后，同一单据永久无法重新提交（陈旧指针把门焊死）。
    if (next == WorkflowInstance.Phase.APPROVED
        || next == WorkflowInstance.Phase.REJECTED
        || next == WorkflowInstance.Phase.WITHDRAWN
        || next == WorkflowInstance.Phase.TERMINATED) {
      jdbc.update(
          WfSql.DELETE_ACTIVE_INSTANCE_BY_INSTANCE, params().addValue("instanceId", instanceId));
    }
    return true;
  }

  @Override
  public boolean claimActiveInstance(WorkflowInstance instance) {
    try {
      insertActivePointer(instance);
      return true;
    } catch (DuplicateKeyException ex) {
      // 已被占用：可能是他人真在启动，也可能是上一次流程终态后遗留的陈旧指针（释放失败 / 崩溃）。
      // 后者若不清理，会把重新提交永久焊死——故按指针指向实例的终态判定后清理重试一次。
      return reclaimIfStale(instance);
    }
  }

  @Override
  public void releaseActiveInstance(String entityCode, String recordId) {
    jdbc.update(
        WfSql.DELETE_ACTIVE_INSTANCE,
        params().addValue("entityCode", entityCode).addValue("recordId", recordId));
  }

  private void insertActivePointer(WorkflowInstance instance) {
    jdbc.update(
        WfSql.INSERT_ACTIVE_INSTANCE,
        params()
            .addValue("entityCode", instance.entityCode())
            .addValue("recordId", instance.recordId())
            .addValue("instanceId", instance.id())
            .addValue("now", Timestamp.from(Instant.now())));
  }

  private boolean reclaimIfStale(WorkflowInstance instance) {
    Optional<WorkflowInstance> holder =
        findInstanceByRecord(instance.entityCode(), instance.recordId());
    if (holder.isPresent() && !holder.get().isTerminated()) {
      return false; // 确有活动实例，不得抢占
    }
    int deleted =
        jdbc.update(
            WfSql.DELETE_ACTIVE_INSTANCE,
            params()
                .addValue("entityCode", instance.entityCode())
                .addValue("recordId", instance.recordId()));
    if (deleted == 0) {
      return false;
    }
    try {
      insertActivePointer(instance);
      return true;
    } catch (DuplicateKeyException ex) {
      return false; // 清理后仍被他人抢到
    }
  }

  @Override
  public boolean compareAndAdvanceNode(
      String instanceId,
      String nodeCode,
      String nextNodeCode,
      WorkflowDefinition.ApprovalMode mode,
      String operatorId) {
    // 关键：判据与推进在同一条 UPDATE 内完成（见 WfSql 上的说明）。此处不得拆成「先查后改」。
    // mode == null（START / CONDITION / CC 等非审批节点）映射为显式 AUTO token——
    // 绝不能折成 SINGLE：那会要求「存在 APPROVED 任务」，非审批节点没有任务 ⇒ 0 行 ⇒ 流程启动即卡死。
    return jdbc.update(
            WfSql.COMPARE_AND_ADVANCE_NODE,
            params()
                .addValue("instanceId", instanceId)
                .addValue("nodeCode", nodeCode)
                .addValue("nextNodeCode", nextNodeCode)
                .addValue("mode", mode == null ? "AUTO" : mode.name())
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("operatorId", operatorId))
        > 0;
  }

  // ====================== 任务 ======================

  @Override
  public void saveTask(WorkflowTask task, String operatorId) {
    jdbc.update(
        WfSql.INSERT_TASK,
        params()
            .addValue("id", task.id())
            .addValue("instanceId", task.instanceId())
            .addValue("nodeCode", task.nodeCode())
            .addValue("assigneeId", task.assigneeId())
            .addValue("status", task.status().name())
            .addValue("decision", task.decision() == null ? null : task.decision().name())
            .addValue("comment", task.comment())
            .addValue("dueAt", task.dueAt() == null ? null : Timestamp.from(task.dueAt()))
            .addValue("now", Timestamp.from(Instant.now()))
            .addValue("operatorId", operatorId));
  }

  @Override
  public Optional<WorkflowTask> findTask(String taskId) {
    List<WorkflowTask> rows =
        jdbc.query(
            WfSql.SELECT_TASK_BY_ID, params().addValue("id", taskId), (rs, n) -> mapTask(rs));
    return rows.stream().findFirst();
  }

  @Override
  public List<WorkflowTask> listPendingTasks(String instanceId) {
    return jdbc.query(
        WfSql.SELECT_PENDING_TASKS,
        params().addValue("instanceId", instanceId),
        (rs, n) -> mapTask(rs));
  }

  @Override
  public List<WorkflowTask> listTasksByNode(String instanceId, String nodeCode) {
    return jdbc.query(
        WfSql.SELECT_TASKS_BY_NODE,
        params().addValue("instanceId", instanceId).addValue("nodeCode", nodeCode),
        (rs, n) -> mapTask(rs));
  }

  @Override
  public List<WorkflowTask> listTasksByAssignee(String assigneeId, WorkflowTask.Status status) {
    return jdbc.query(
        WfSql.SELECT_TASKS_BY_ASSIGNEE,
        params().addValue("assigneeId", assigneeId).addValue("status", status.name()),
        (rs, n) -> mapTask(rs));
  }

  @Override
  public List<WorkflowTask> listTasks(String instanceId) {
    return jdbc.query(
        WfSql.SELECT_TASKS_BY_INSTANCE,
        params().addValue("instanceId", instanceId),
        (rs, n) -> mapTask(rs));
  }

  @Override
  public boolean compareAndSetTaskStatus(
      String taskId,
      WorkflowTask.Status expected,
      WorkflowTask.Status next,
      WorkflowTask.Decision decision,
      String comment,
      String operatorId) {
    return jdbc.update(
            WfSql.COMPARE_AND_SET_TASK_STATUS,
            params()
                .addValue("id", taskId)
                .addValue("expected", expected.name())
                .addValue("next", next.name())
                .addValue("decision", decision == null ? null : decision.name())
                .addValue("comment", comment)
                .addValue("decidedAt", Timestamp.from(Instant.now()))
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("operatorId", operatorId))
        > 0;
  }

  @Override
  public boolean reassignTask(String taskId, String newAssigneeId, String operatorId) {
    return jdbc.update(
            WfSql.REASSIGN_TASK,
            params()
                .addValue("id", taskId)
                .addValue("newAssigneeId", newAssigneeId)
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("operatorId", operatorId))
        > 0;
  }

  @Override
  public void cancelPendingTasks(String instanceId, String operatorId) {
    jdbc.update(
        WfSql.CANCEL_PENDING_TASKS,
        params()
            .addValue("instanceId", instanceId)
            .addValue("updatedAt", Timestamp.from(Instant.now()))
            .addValue("operatorId", operatorId));
  }

  @Override
  public void cancelPendingTasksOfNode(String instanceId, String nodeCode, String operatorId) {
    jdbc.update(
        WfSql.CANCEL_PENDING_TASKS_OF_NODE,
        params()
            .addValue("instanceId", instanceId)
            .addValue("nodeCode", nodeCode)
            .addValue("updatedAt", Timestamp.from(Instant.now()))
            .addValue("operatorId", operatorId));
  }

  // ====================== 内部 ======================

  /** 统一注入 tenantId；缺失即失败关闭（引擎无租户概念，隔离不能因此失效）。 */
  private MapSqlParameterSource params() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流桥接层缺少租户上下文（TenantContext）——拒绝在未知租户下读写 wf_* 数据");
    }
    return new MapSqlParameterSource("tenantId", tenantId);
  }

  private MapSqlParameterSource instanceParams(WorkflowInstance instance, String operatorId) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", instance.id());
    values.put("entityCode", instance.entityCode());
    values.put("recordId", instance.recordId());
    values.put("defKey", instance.definitionKey());
    values.put("defVersion", instance.definitionVersion());
    values.put("currentNodeCode", instance.currentNodeCode());
    values.put("phase", instance.phase().name());
    values.put("startedBy", instance.startedBy());
    values.put(
        "startedAt", instance.startedAt() == null ? null : Timestamp.from(instance.startedAt()));
    values.put(
        "updatedAt", instance.updatedAt() == null ? null : Timestamp.from(instance.updatedAt()));
    // 真实操作人（驳回/撤回/恢复/审批通过/发起的当事人），不是发起人——否则实例行「谁最后动了这单」恒答不出
    values.put("operatorId", operatorId);
    values.put("now", Timestamp.from(Instant.now()));
    values.put("tenantId", TenantContext.getTenantIdAsLong());
    return new MapSqlParameterSource(values);
  }

  private WorkflowInstance mapInstance(ResultSet rs) throws java.sql.SQLException {
    return new WorkflowInstance(
        rs.getString("id"),
        rs.getString("entity_code"),
        rs.getString("record_id"),
        rs.getString("def_key"),
        rs.getInt("def_version"),
        rs.getString("current_node_code"),
        WorkflowInstance.Phase.valueOf(rs.getString("phase")),
        rs.getString("started_by"),
        toInstant(rs.getTimestamp("started_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private WorkflowTask mapTask(ResultSet rs) throws java.sql.SQLException {
    String decision = rs.getString("decision");
    return new WorkflowTask(
        rs.getString("id"),
        rs.getString("instance_id"),
        rs.getString("node_code"),
        rs.getString("assignee_id"),
        WorkflowTask.Status.valueOf(rs.getString("status")),
        decision == null ? null : WorkflowTask.Decision.valueOf(decision),
        rs.getString("comment"),
        toInstant(rs.getTimestamp("due_at")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("decided_at")));
  }

  private Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
