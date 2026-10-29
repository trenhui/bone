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
import com.bone.workflow.ports.spi.WorkflowInboxQuery;
import com.bone.workflow.ports.spi.WorkflowNotification;
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
  private final ObjectProvider<WorkflowInboxQuery> inboxProvider;
  private final RuntimeRecordApplicationService records;
  private final RuntimeAccessGuard accessGuard;
  private final CurrentUserProvider currentUser;

  public RuntimeApprovalApplicationService(
      ObjectProvider<WorkflowEngine> engineProvider,
      ObjectProvider<WorkflowInboxQuery> inboxProvider,
      RuntimeRecordApplicationService records,
      RuntimeAccessGuard accessGuard,
      CurrentUserProvider currentUser) {
    this.engineProvider = Objects.requireNonNull(engineProvider, "engineProvider");
    this.inboxProvider = Objects.requireNonNull(inboxProvider, "inboxProvider");
    this.records = Objects.requireNonNull(records, "records");
    this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard");
    this.currentUser = Objects.requireNonNull(currentUser, "currentUser");
  }

  /**
   * 我的待办（跨单据 / 跨实体）。
   *
   * <p><b>分页下推</b>：limit/offset 与排序由存储层完成（{@code ORDER BY created_at DESC}），count 走同判据的
   * COUNT——此前「全量载入 + 内存裁页」把数据量做成随使用时长线性膨胀的热点；单据摘要只对<b>本页</b> ≤size 条回表， N+1 从「全部任务」降为「一页任务」。
   *
   * @param page 页码（1 起）
   * @param size 每页条数
   */
  public PageResult<Map<String, Object>> listPendingTasks(int page, int size) {
    int[] bounds = clampPaging(page, size);
    String current = requireOperatorForTasks();
    WorkflowEngine engine = requireEngine();
    long total = call(() -> engine.countPendingTasksByAssignee(current));
    List<WorkflowTask> tasks =
        call(() -> engine.listPendingTasksByAssignee(current, bounds[0], bounds[1]));
    return PageResult.of(summarizePage(engine, tasks), total, bounds[2], bounds[0]);
  }

  /**
   * 我的已办（已表态任务，含通过 / 驳回 / 已转交 / 已作废，按决策时间倒序）。
   *
   * <p>分页同样下推：此前按状态逐个全量加载再内存排序裁页（状态数 × 全量），现一次 count + 一页 SQL 完成。
   */
  public PageResult<Map<String, Object>> listDecidedTasks(int page, int size) {
    int[] bounds = clampPaging(page, size);
    String current = requireOperatorForTasks();
    WorkflowEngine engine = requireEngine();
    long total = call(() -> engine.countDecidedTasksByAssignee(current));
    List<WorkflowTask> tasks =
        call(() -> engine.listDecidedTasksByAssignee(current, bounds[0], bounds[1]));
    return PageResult.of(summarizePage(engine, tasks), total, bounds[2], bounds[0]);
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
    // 挂起原因（诊断）：挂起时引擎不派单，详情页只看到「挂起 + 零待办」时，发起人/审批人无法判断
    // 是「审批人解析不出」「审批人就是发起人本人」还是「组织信息缺失」。原因真源是挂起告警正文，
    // 读回来一并返回；查不到就不返回该字段（前端降级为通用指引），不让诊断信息缺失影响整个详情。
    if (instance.isSuspended()) {
      suspendReason(instance).ifPresent(reason -> result.put("suspendReason", reason));
    }
    return result;
  }

  /** 挂起原因：来自通知端口（缺省实现 = 空，宿主把通知接 IM/邮件时不落本地表）。 */
  private Optional<String> suspendReason(WorkflowInstance instance) {
    Long tenantId = com.bone.core.tenant.context.TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      return Optional.empty();
    }
    // getIfUnique 而非 getIfAvailable：默认通知端口写读一体，容器里按读端口类型看有多个候选。
    // 诊断信息取不到只是少一句话，绝不能让它抛 Bean 歧义异常把整个单据详情打成 500。
    return Optional.ofNullable(inboxProvider.getIfUnique())
        .flatMap(query -> query.latestSuspensionNotice(tenantId, instance.id()));
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

  /**
   * 转办：办理人把<b>自己的</b>待办转给他人（出差 / 请假 / 越权发现），{"assigneeId":"...","comment":"..."}。
   *
   * <p>与 {@link #reassign} 的语义分工：转办是<b>办理人自助</b>（引擎校验操作人 == 当前办理人），改派是<b>管理员干预</b>
   * （写权限即可，不要求是办理人）。两者都走 PENDING 条件更新——办理人可能正在点同意，先查后改会改出「已办结却被转」的幽灵任务。
   */
  public Map<String, Object> transfer(String taskId, Map<String, Object> body) {
    String assigneeId = requireText(body, "assigneeId");
    String comment = text(body.get("comment"));
    WorkflowTask task =
        call(() -> requireEngine().transferTask(taskId, assigneeId, operator(), comment));
    return Map.of(
        "taskId", task.id(), "assigneeId", task.assigneeId(), "status", task.status().name());
  }

  /**
   * 加签：办理人拉人会办当前节点，{"assigneeId":"...","comment":"..."}——被加签人拿到同节点 PENDING， 节点完成判定（会签 /
   * 单签）自动把它计入，不改流程拓扑。
   */
  public Map<String, Object> addSign(String taskId, Map<String, Object> body) {
    String assigneeId = requireText(body, "assigneeId");
    String comment = text(body.get("comment"));
    WorkflowTask task =
        call(() -> requireEngine().addSign(taskId, assigneeId, operator(), comment));
    return Map.of(
        "taskId", task.id(), "assigneeId", task.assigneeId(), "status", task.status().name());
  }

  /**
   * 恢复挂起实例：组织数据修复后让 {@code SUSPENDED} 死单回到 {@code RUNNING}。
   *
   * <p>与 {@link #assign} 的分工：assign 是「直接注入办理人」（人已知），resume 是「重跑解析」（指派规则数据已修好，
   * 让引擎按原规则重新解析）。解析仍不出人时引擎保持挂起并报错——失败关闭不放行。
   */
  public Map<String, Object> resume(String instanceId, Map<String, Object> body) {
    // reason 仅审计留痕：恢复动作本身由引擎 CAS 防并发，body 无必填项（与 terminate 的不可逆语义不同）。
    WorkflowInstance instance = call(() -> requireEngine().resume(instanceId, operator()));
    Map<String, Object> row = snapshot(instance);
    if (body != null && body.get("reason") != null) {
      row.put("reason", text(body.get("reason")));
    }
    return row;
  }

  /**
   * 我的站内信（收件箱）：待办派单 / 催办 / 转办 / 挂起 / 抄送的投影，{@code unreadOnly=true} 只看未读。
   *
   * <p>接收人即当前操作人（与待办同一身份口径），不接受入参指定他人——收件箱是「我的」，允许指定 recipientId 等于开任意他人信箱的口子。
   */
  public List<Map<String, Object>> inbox(boolean unreadOnly) {
    String current = operator();
    if (current == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, "匿名主体没有收件箱（按登录用户检索）");
    }
    WorkflowInboxQuery inbox = requireInboxQuery();
    long tenantId = currentTenantId();
    return inbox.inbox(tenantId, current, unreadOnly).stream()
        .map(RuntimeApprovalApplicationService::toInboxRow)
        .toList();
  }

  /**
   * 问题单据列表：按阶段列举实例（{@code SUSPENDED} 挂起死单 / {@code REJECTED} 驳回单等），更新时间倒序，超限截断。
   *
   * <p><b>只截断不计数</b>：排障入口不是分页浏览——相位随处置实时变化，count 与列表两次查询天然不一致； {@code truncated=true} 即提示「不止这些」。上限
   * 200，防止排障页一次拉空整张表。
   */
  public Map<String, Object> instancesByPhase(String phase, int limit) {
    WorkflowInstance.Phase parsed = requirePhase(phase);
    WorkflowEngine engine = requireEngine();
    int capped = Math.min(Math.max(limit, 1), 200);
    List<WorkflowInstance> found = call(() -> engine.listInstancesByPhase(parsed, capped));
    List<Map<String, Object>> rows = found.stream().map(this::snapshot).toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("phase", parsed.name());
    result.put("truncated", rows.size() >= capped);
    result.put("instances", rows);
    return result;
  }

  private static WorkflowInstance.Phase requirePhase(String phase) {
    if (phase == null || phase.isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "缺少必填参数：phase");
    }
    try {
      return WorkflowInstance.Phase.valueOf(phase.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "未知的流程阶段：" + phase + "（可选：RUNNING/SUSPENDED/APPROVED/REJECTED/WITHDRAWN/TERMINATED）");
    }
  }

  /** 收件箱端口：缺失即 422（宿主没装 starter 或覆盖成无读端口的实现），不让 Spring 上下文启动失败。 */
  private WorkflowInboxQuery requireInboxQuery() {
    WorkflowInboxQuery inbox = inboxProvider.getIfAvailable();
    if (inbox == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "站内信收件箱端口未装配（WorkflowInboxQuery 缺失），通知类端点不可用");
    }
    return inbox;
  }

  private long currentTenantId() {
    try {
      Long tenant = com.bone.core.tenant.context.TenantContext.getTenantIdAsLong();
      return tenant == null ? 0L : tenant;
    } catch (RuntimeException | Error ignored) {
      return 0L;
    }
  }

  private static Map<String, Object> toInboxRow(WorkflowNotification n) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", n.id());
    row.put("type", n.type());
    row.put("instanceId", n.instanceId());
    row.put("entityCode", n.entityCode());
    row.put("recordId", n.recordId());
    row.put("nodeCode", n.nodeCode());
    row.put("title", n.title());
    row.put("body", n.body());
    row.put("read", n.read());
    row.put("createdAt", n.createdAt());
    return row;
  }

  // ── 内部 ────────────────────────────────────────────────────────────────

  /**
   * 分页参数夹取：{@code [size, offset, page]}。
   *
   * <p>size 夹 1..200（HTTP 参数不夹上限等于允许一次拉空整张表），page 夹 ≥1（负页在下推 offset 时会算出负 OFFSET → SQL 语法错误 500）。
   */
  private static int[] clampPaging(int page, int size) {
    int clampedSize = Math.min(Math.max(size, 1), 200);
    int clampedPage = Math.max(page, 1);
    return new int[] {clampedSize, (clampedPage - 1) * clampedSize, clampedPage};
  }

  /** 待办 / 已办检索的操作人：匿名主体没有「我的」任何东西，明确 403 而非返回空列表误导。 */
  private String requireOperatorForTasks() {
    String current = operator();
    if (current == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, "匿名主体没有待办（待办以登录用户为办理人检索）");
    }
    return current;
  }

  /** 本页任务 → 行投影（附单据摘要）。实例反查与摘要回表只发生在<b>页内</b> ≤size 条上——全量时代的每任务一查（N+1）由此封顶。 */
  private List<Map<String, Object>> summarizePage(WorkflowEngine engine, List<WorkflowTask> tasks) {
    List<Map<String, Object>> rows = new ArrayList<>(tasks.size());
    for (WorkflowTask task : tasks) {
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
