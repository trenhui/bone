package com.bone.metadata.runtime.application;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 审批工作流入站应用服务（我的待办 / 已办 / 单据审批概况 / 管理员干预）。
 *
 * <p><b>为什么单独成服务</b>：运行时 REST 面此前只有「记录 CRUD + 触发动作」，审批流数据（待办、轨迹、实例投影）没有 HTTP 出口——
 * 前端只能拿「approver_id=$me 过滤列表」近似凑「待我审批」，而该近似在「任务已改派 / 指派快照与单据字段不一致」时立刻失真 （改派只改 {@code
 * wf_task.assignee_id}，不回写单据字段）。待办的真源只能是 {@code wf_task}。
 *
 * <p><b>授权边界</b>：待办可见性判据是<b>任务指派快照</b>（{@code assigneeId == 当前操作者}），由仓储 SQL 直接限定，与行级策略 {@code
 * owner_or_approver} 无关——「被指派审批」本身即是读依据。单据摘要额外过一层实体级读权限；行级不可见时摘要降级为 null，不影响待办条目返回。
 *
 * <p>引擎引用经 {@link ObjectProvider} 惰性解析：未引入 {@code bone-workflow-spring-boot-starter}
 * 的宿主页不装配引擎，此时本服务报可诊断的 422，而不是让 Spring 上下文启动失败。
 */
@Service
@Slf4j
public class RuntimeApprovalApplicationService {

  /** 待办列表里附带展示的单据字段（只读展示，不构成任何校验口径）。 */
  private static final List<String> SUMMARY_FIELDS =
      List.of("claim_no", "advance_no", "title", "amount", "currency", "status", "applicant");

  private final ObjectProvider<WorkflowEngine> engineProvider;
  private final RuntimeRecordApplicationService records;
  private final RuntimeAccessGuard accessGuard;
  private final CurrentUserProvider currentUser;

  public RuntimeApprovalApplicationService(
      ObjectProvider<WorkflowEngine> engineProvider,
      RuntimeRecordApplicationService records,
      RuntimeAccessGuard accessGuard,
      CurrentUserProvider currentUser) {
    this.engineProvider = Objects.requireNonNull(engineProvider, "engineProvider");
    this.records = Objects.requireNonNull(records, "records");
    this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard");
    this.currentUser = Objects.requireNonNull(currentUser, "currentUser");
  }

  /**
   * 我的待办（跨单据 / 跨实体）。
   *
   * @param page 页码（1 起）
   * @param size 每页条数
   */
  public PageResult<Map<String, Object>> listPendingTasks(int page, int size) {
    return paginate(queryTasks(WorkflowTask.Status.PENDING), page, size);
  }

  /** 我的已办（已表态任务，含通过 / 驳回 / 已转交 / 已作废，按决策时间倒序）。 */
  public PageResult<Map<String, Object>> listDecidedTasks(int page, int size) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (WorkflowTask.Status status : WorkflowTask.Status.values()) {
      if (status != WorkflowTask.Status.PENDING) {
        rows.addAll(queryTasks(status));
      }
    }
    rows.sort(
        (a, b) -> String.valueOf(b.get("decidedAt")).compareTo(String.valueOf(a.get("decidedAt"))));
    return paginate(rows, page, size);
  }

  /**
   * 单据审批概况：实例投影 + 当前待办 + 完整审批轨迹（详情页「审批时间线」直接用）。
   *
   * <p>无实例时返回 {@code started=false}——未接工作流的单据（历史数据 / 未逃逸实体）没有审批过程，不是错误。
   */
  public Map<String, Object> describe(String entityCode, String recordId) {
    accessGuard.requireRead(entityCode);
    WorkflowEngine engine = requireEngine();
    Optional<WorkflowInstance> found = engine.findInstance(entityCode, recordId);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("entityCode", entityCode);
    result.put("recordId", recordId);
    if (found.isEmpty()) {
      result.put("started", Boolean.FALSE);
      return result;
    }
    WorkflowInstance instance = found.get();
    result.put("started", Boolean.TRUE);
    result.put("instance", snapshot(instance));
    result.put(
        "pendingTasks",
        engine.listPendingTasks(instance.id()).stream().map(this::snapshot).toList());
    result.put(
        "trail", engine.listInstanceHistory(instance.id()).stream().map(this::snapshot).toList());
    return result;
  }

  /** 改派：把待办转给他人（请假 / 离职 / 指派错人）；引擎侧按 PENDING 条件更新，已被他人处理则失败。 */
  public Map<String, Object> reassign(String taskId, Map<String, Object> body) {
    String assigneeId = requireText(body, "assigneeId");
    String reason = text(body.get("reason"));
    WorkflowTask task =
        call(() -> requireEngine().reassignTask(taskId, assigneeId, operator(), reason));
    return Map.of(
        "taskId", task.id(), "assigneeId", task.assigneeId(), "status", task.status().name());
  }

  /** 催办：只发通知 + 留痕，不改任务状态（提醒不是决策）。 */
  public Map<String, Object> urge(String taskId, Map<String, Object> body) {
    String comment = text(body.get("comment"));
    WorkflowTask task = call(() -> requireEngine().urgeTask(taskId, operator(), comment));
    return Map.of("taskId", task.id(), "assigneeId", task.assigneeId());
  }

  /** 强制指派：打破「审批人解析不出 → 挂起」的死单（{@code SUSPENDED} 且无任务时注入待办并恢复）。 */
  public Map<String, Object> assign(String instanceId, Map<String, Object> body) {
    String assigneeId = requireText(body, "assigneeId");
    String nodeCode = text(body.get("nodeCode"));
    String reason = text(body.get("reason"));
    WorkflowInstance instance =
        call(
            () -> requireEngine().assignTask(instanceId, nodeCode, assigneeId, operator(), reason));
    return snapshot(instance);
  }

  /** 强制终止：管理员对挂起死单 / 错提单据的不可逆处置（与发起人撤回语义区分）。 */
  public Map<String, Object> terminate(String instanceId, Map<String, Object> body) {
    String reason = requireText(body, "reason");
    WorkflowInstance instance =
        call(() -> requireEngine().terminateInstance(instanceId, operator(), reason));
    return snapshot(instance);
  }

  // ── 内部 ────────────────────────────────────────────────────────────────

  private List<Map<String, Object>> queryTasks(WorkflowTask.Status status) {
    String current = operator();
    if (current == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, "匿名主体没有待办（待办以登录用户为办理人检索）");
    }
    WorkflowEngine engine = requireEngine();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (WorkflowTask task : call(() -> engine.listTasksByAssignee(current, status))) {
      Map<String, Object> row = new LinkedHashMap<>(snapshot(task));
      Optional<WorkflowInstance> found = call(() -> engine.findInstanceById(task.instanceId()));
      if (found.isPresent()) {
        WorkflowInstance instance = found.get();
        row.put("entityCode", instance.entityCode());
        row.put("recordId", instance.recordId());
        row.put("instancePhase", instance.phase().name());
        row.put("currentNode", instance.currentNodeCode());
        row.put("summary", summarize(instance));
      } else {
        // 实例缺失（数据被清理 / 跨表残留）：任务仍列出但标星，便于对账而非静默吞掉。
        row.put("instanceMissing", Boolean.TRUE);
      }
      rows.add(row);
    }
    return rows;
  }

  /** 单据摘要：行级不可见或实体无对应列时降级为 null，不让摘要失败拖垮待办主列表。 */
  private Map<String, Object> summarize(WorkflowInstance instance) {
    try {
      accessGuard.requireRead(instance.entityCode());
      Map<String, Object> record = records.getById(instance.entityCode(), instance.recordId());
      if (record == null) {
        return null;
      }
      Map<String, Object> summary = new LinkedHashMap<>();
      for (String field : SUMMARY_FIELDS) {
        Object value = record.get(field);
        if (value != null) {
          summary.put(field, value);
        }
      }
      return summary;
    } catch (RuntimeException ex) {
      log.warn(
          "[approval] 待办单据摘要不可见 entity={} record={} reason={}",
          instance.entityCode(),
          instance.recordId(),
          ex.getMessage());
      return null;
    }
  }

  private PageResult<Map<String, Object>> paginate(
      List<Map<String, Object>> rows, int page, int size) {
    // 分页在内存裁剪：仓储侧暂未下推 limit/offset。数据量为「一个办理人的在办任务」，可控；
    // 单个办理人待办超过数千条时需先在下沉 SQL 加 limit，否则全量载入成为热点。
    int from = Math.min(Math.max(page - 1, 0) * size, rows.size());
    int to = Math.min(from + size, rows.size());
    return PageResult.of(rows.subList(from, to), (long) rows.size(), page, size);
  }

  private WorkflowEngine requireEngine() {
    WorkflowEngine engine = engineProvider.getIfAvailable();
    if (engine == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "审批流引擎未装配（bone-workflow-spring-boot-starter 端口缺失），审批类端点不可用");
    }
    return engine;
  }

  private String operator() {
    Long userId = currentUser.currentUserIdOrNull();
    return userId == null ? null : String.valueOf(userId);
  }

  private static <T> T call(Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (WorkflowEngineException ex) {
      throw translate(ex);
    }
  }

  /** 引擎异常 → 平台稳定错误码（按 {@link WorkflowErrorCode} 的 HTTP 语义映射，不拼消息字符串，防两处口径漂移）。 */
  private static RuntimeRecordException translate(WorkflowEngineException ex) {
    int httpStatus = ex.code().httpStatus();
    if (httpStatus == 403) {
      return new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, ex.getMessage());
    }
    if (httpStatus == 409) {
      return new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION, ex.getMessage());
    }
    return new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getMessage());
  }

  private Map<String, Object> snapshot(WorkflowInstance instance) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("instanceId", instance.id());
    row.put("entityCode", instance.entityCode());
    row.put("recordId", instance.recordId());
    row.put("definitionKey", instance.definitionKey());
    row.put("definitionVersion", instance.definitionVersion());
    row.put("phase", instance.phase().name());
    row.put("currentNode", instance.currentNodeCode());
    row.put("startedBy", instance.startedBy());
    row.put("startedAt", instance.startedAt());
    row.put("updatedAt", instance.updatedAt());
    row.put("terminated", instance.isTerminated());
    row.put("suspended", instance.isSuspended());
    return row;
  }

  private Map<String, Object> snapshot(WorkflowTask task) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("taskId", task.id());
    row.put("instanceId", task.instanceId());
    row.put("nodeCode", task.nodeCode());
    row.put("assigneeId", task.assigneeId());
    row.put("status", task.status().name());
    row.put("decision", task.decision() == null ? null : task.decision().name());
    row.put("comment", task.comment());
    row.put("createdAt", task.createdAt());
    row.put("decidedAt", task.decidedAt());
    row.put("pending", task.isPending());
    return row;
  }

  private static String requireText(Map<String, Object> body, String key) {
    String value = text(body == null ? null : body.get(key));
    if (value == null || value.isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "缺少必填参数：" + key);
    }
    return value;
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
