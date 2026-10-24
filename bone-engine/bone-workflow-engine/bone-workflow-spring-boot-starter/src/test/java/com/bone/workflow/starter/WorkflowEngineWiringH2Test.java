package com.bone.workflow.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.outbox.OutboxStatusPort;
import com.bone.workflow.starter.outbox.StatusIntent;
import com.bone.workflow.starter.outbox.StatusIntentExecutor;
import com.bone.workflow.starter.outbox.StatusProjectionRelay;
import com.bone.workflow.starter.outbox.WorkflowStatusOutboxRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * <b>真引擎 + 真 SDK 适配器 + 真发件箱</b>的端到端接线测试（H2 原样执行生产 SQL）。
 *
 * <p><b>为什么必须有这一层</b>：runtime 单测用内存 stub（{@code mode==null} 无条件移动，与引擎意图一致），适配器单测从 APPROVE
 * 节点推进——两侧各自全绿， 却掩盖了「生产 SQL 推不动 START / CONDITION 节点」这类<b>接线缺陷</b>（P0-1：适配器把 null 折成 SINGLE ⇒
 * 非审批节点找不到 APPROVED 任务 ⇒ 0 行 ⇒ 流程启动即静默卡死）。只有让引擎经真实仓储跑完整链路，这类缺陷才会现形。
 *
 * <p>链路：{@code WorkflowEngineImpl} → {@link WorkflowDefinitionRepository} / {@link
 * WorkflowInstanceRepository}（均由 {@link WorkflowBoneIntegrationAutoConfiguration} 经 SDK
 * {@code @Sql} 装配）→ {@link OutboxStatusPort} → {@link StatusProjectionRelay}，全部生产实现， 仅指派解析 / 单据数据 /
 * 审计 / id 生成用测试替身。
 *
 * <p><b>适配器来自生产装配而非 {@code new}</b>：2026-10-24 持久化收敛到 bone-metadata-sdk 后，适配器的正确性一半在「bean 链起得来」（五个
 * SDK 基建 bean + 扩展仓储扫描 + 仓储代理注册）。直接从上下文按<b>端口类型</b>取用， 装配缺陷（缺 bean / 条件恒假 / 注册遗漏）会在此现形，而不是到宿主启动才爆炸。
 */
class WorkflowEngineWiringH2Test {

  private static final long TENANT = 1L;

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();
  private WorkflowEngine engine;
  private WorkflowStatusOutboxRepository outbox;
  private final List<String> appliedActions = new ArrayList<>();
  private final AtomicInteger idSeq = new AtomicInteger();

  @BeforeEach
  void setUp() throws Exception {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    outbox = context.getBean(WorkflowStatusOutboxRepository.class);

    seedDefinition("expense", true);
    seedDefinition("expense_offline", false);

    WorkflowStatusPort statusPort =
        new OutboxStatusPort(outbox, () -> "OBX" + idSeq.incrementAndGet());

    engine =
        new WorkflowEngineImpl(
            context.getBean(WorkflowDefinitionRepository.class),
            context.getBean(WorkflowInstanceRepository.class),
            approverResolver(),
            recordDataPort(),
            statusPort,
            auditPort(),
            idGenerator(),
            new StructuredConditionEvaluator(),
            new WorkflowDefinitionValidator(),
            Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("E2E：start 穿过 START/CONDITION 非审批节点并派出首个审批任务（P0-1 回归）")
  void start_advancesThroughNonApprovalNodes_andCreatesFirstTask() {
    WorkflowInstance instance = engine.start("expense_reimbursement", "r-w1", "expense", "u1");

    // 旧缺陷下实例会永远停在 start、零任务：推进 SQL 对非审批节点要求「存在 APPROVED 任务」
    assertEquals("big", instance.currentNodeCode(), "amount=8000 > 5000 必须走高价分支");
    List<WorkflowTask> pending = engine.listPendingTasks(instance.id());
    assertEquals(1, pending.size(), "必须真的派出审批任务（零任务 = 流程静默卡死）");
    assertEquals("mgr1", pending.get(0).assigneeId());
    assertEquals(WorkflowTask.Status.PENDING, pending.get(0).status());
  }

  @Test
  @DisplayName("E2E：审批通过 → 实例终态 → outbox 意图 → 中继落地（全链路真实现）")
  void approve_completesFlow_landsOutboxIntentThroughRelay() {
    WorkflowInstance instance = engine.start("expense_reimbursement", "r-w2", "expense", "u1");
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);

    WorkflowInstance approved =
        engine.completeTask(task.id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");

    assertEquals(WorkflowInstance.Phase.APPROVED, approved.phase());
    assertEquals("end", approved.currentNodeCode());
    assertEquals(0, engine.listPendingTasks(approved.id()).size(), "终态不得留待办");

    // 发件箱里必须有一条 APPROVED 意图（同步投影丢失的最后防线）
    List<StatusIntent> due = outbox.pollDue(10);
    assertEquals(1, due.size(), "审批通过必须落一条状态投影意图");
    assertEquals("APPROVED", due.get(0).intent());
    assertEquals("approve", due.get(0).actionCode());

    // 中继（模拟无租户上下文的 @Scheduled 线程）把意图真正落地
    TenantContext.clear();
    StatusProjectionRelay relay = new StatusProjectionRelay(outbox, recordingExecutor(), 100, 3);
    assertEquals(1, relay.runOnce(10), "中继必须把意图落地");
    assertEquals(List.of(instance.id() + ":approve"), appliedActions);
  }

  @Test
  @DisplayName("E2E：实例被并发撤回后，迟到审批必须被拒绝而非落库（P1-7 纵深防御回归）")
  void lateApproval_afterWithdraw_isRejected() {
    WorkflowInstance instance = engine.start("expense_reimbursement", "r-w4", "expense", "u1");
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);

    engine.withdraw(instance.id(), "u1");

    WorkflowEngineException ex =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(task.id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1"));
    assertEquals(
        WorkflowErrorCode.INSTANCE_TERMINATED, ex.code(), "实例已撤回，迟到审批必须被拒——否则审计里出现「撤回流程中有人同意」");

    // 纵深防御：即使引擎漏判，SQL 层的实例终态守卫也不允许任务被改写
    assertTrue(
        outbox.pollDue(10).stream().noneMatch(i -> "APPROVED".equals(i.intent())),
        "撤回的流程不得产生 APPROVED 投影意图");
  }

  @Test
  @DisplayName("E2E：运营下线（enabled=0）的定义不得再被新提交选中（P1-1 回归）")
  void disabledDefinition_isNotSelected() {
    WorkflowEngineException ex =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.start("expense_reimbursement", "r-w3", "expense_offline", "u1"));
    assertEquals(WorkflowErrorCode.DEFINITION_NOT_FOUND, ex.code());

    // 重新启用后可正常启动（证明上面失败确实源于 enabled 过滤，而非定义本身有问题）
    jdbc.update("UPDATE wf_process_def SET enabled = 1 WHERE def_key = 'expense_offline'");
    WorkflowInstance instance =
        engine.start("expense_reimbursement", "r-w3", "expense_offline", "u1");
    assertFalse(instance.isTerminated());
    assertEquals("big", instance.currentNodeCode());
  }

  // ====================== 夹具 ======================

  /**
   * 报销示例定义：START → CONDITION(amount>5000 ? big : small) → END。
   *
   * <p>CONDITION 的两条出边都通向 APPROVE 节点，配合单据数据 amount=8000 走 {@code big}—— 同时覆盖非审批节点推进与条件分支求值。
   */
  private void seedDefinition(String defKey, boolean enabled) throws Exception {
    JsonNode bigCondition =
        mapper.readTree("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            defKey,
            "报销审批",
            1,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node(
                    "cond", WorkflowDefinition.NodeType.CONDITION, null, "金额分流"),
                new WorkflowDefinition.Node(
                    "big",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "高价审批"),
                new WorkflowDefinition.Node(
                    "small",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "普通审批"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(
                new WorkflowDefinition.Edge("start", "cond", null, null),
                new WorkflowDefinition.Edge("cond", "big", bigCondition, "金额>5000"),
                new WorkflowDefinition.Edge("cond", "small", null, "默认"),
                new WorkflowDefinition.Edge("big", "end", null, null),
                new WorkflowDefinition.Edge("small", "end", null, null)),
            Map.of(
                "big",
                    List.of(
                        new WorkflowDefinition.AssignmentRule("USER", Map.of("userId", "mgr1"))),
                "small",
                    List.of(
                        new WorkflowDefinition.AssignmentRule("USER", Map.of("userId", "mgr2")))));
    jdbc.update(
        "INSERT INTO wf_process_def (id, tenant_id, def_key, def_name, def_version, status,"
            + " enabled, definition_json, created_by, created_at, updated_by, updated_at,"
            + " deleted, version)"
            + " VALUES (?, ?, ?, ?, 1, 'PUBLISHED', ?, ?, 'u1', NOW(), 'u1', NOW(), 0, 0)",
        "D-" + defKey,
        TENANT,
        defKey,
        "报销审批",
        enabled ? 1 : 0,
        mapper.writeValueAsString(definition));
  }

  /** 指派解析替身：USER 策略解析出 params.userId（模拟组织目录按人指派）。 */
  private ApproverResolver approverResolver() {
    return (rule, node, ctx) ->
        rule.params().containsKey("userId")
            ? List.of(String.valueOf(rule.params().get("userId")))
            : List.of();
  }

  private WorkflowRecordPort recordDataPort() {
    return (entityCode, recordId) -> Map.of("amount", 8000);
  }

  private WorkflowAuditPort auditPort() {
    return new WorkflowAuditPort() {
      @Override
      public void recordStart(WorkflowInstance instance, String operatorId) {}

      @Override
      public void recordAdvance(
          WorkflowInstance instance, String fromNode, String toNode, String branchLabel) {}

      @Override
      public void recordDecision(
          WorkflowTask task, WorkflowTask.Decision decision, String comment) {}

      @Override
      public void recordTermination(WorkflowInstance instance, String operatorId, String reason) {}

      @Override
      public void recordSuspension(WorkflowInstance instance, String reason) {}

      @Override
      public void recordResumption(WorkflowInstance instance, String operatorId) {}

      @Override
      public void recordReassignment(
          WorkflowTask task, String newAssigneeId, String operatorId, String reason) {}

      @Override
      public void recordUrge(WorkflowTask task, String operatorId, String comment) {}
    };
  }

  private WorkflowIdGenerator idGenerator() {
    return () -> "W" + idSeq.incrementAndGet();
  }

  private StatusIntentExecutor recordingExecutor() {
    return intent -> {
      if (intent.actionCode() == null) {
        return;
      }
      appliedActions.add(intent.instanceId() + ":" + intent.actionCode());
    };
  }
}
