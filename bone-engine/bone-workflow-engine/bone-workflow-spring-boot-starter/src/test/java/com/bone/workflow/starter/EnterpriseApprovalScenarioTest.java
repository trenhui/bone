package com.bone.workflow.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinition.ApprovalMode;
import com.bone.workflow.domain.WorkflowDefinition.AssignmentRule;
import com.bone.workflow.domain.WorkflowDefinition.Edge;
import com.bone.workflow.domain.WorkflowDefinition.Node;
import com.bone.workflow.domain.WorkflowDefinition.NodeType;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowInstance.Phase;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.domain.WorkflowTask.Decision;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.adapter.PersistenceNotificationPort;
import com.bone.workflow.starter.domain.WorkflowNotification;
import com.bone.workflow.starter.outbox.OutboxStatusPort;
import com.bone.workflow.starter.outbox.WorkflowStatusOutboxRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.bone.workflow.starter.spi.InMemoryOrgHierarchyProvider;
import com.bone.workflow.starter.spi.OrgHierarchyProvider;
import com.bone.workflow.starter.spi.RoleMemberProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
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
 * 企业级报销 / 采购审批的端到端场景测试（真引擎 + 真 SDK 适配器 + 真站内信落库，H2 原样执行生产 SQL）。
 *
 * <p>目标：证明工作流引擎可用于企业复杂的报销、采购审批场景——覆盖金额条件分流、直属主管 / 部门负责人 / 向上 N 级 / 角色
 * 多类指派、串行多层级、会签（全员通过才通过）、以及组织数据缺失时的失败关闭（挂起 + 管理员告警）。 同时验证「派单即通知」：每个待办都落一条站内信，办理人收件箱可见。
 */
class EnterpriseApprovalScenarioTest {

  private static final long TENANT = 1L;
  private static final String INITIATOR = "u1";

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private ObjectMapper mapper;
  private WorkflowStatusOutboxRepository outbox;
  private PersistenceNotificationPort notificationPort;
  private final AtomicInteger idSeq = new AtomicInteger();

  // 组织目录：u1 → mgr1 → mgr2 → mgr3（mgr2 即「向上 2 级」，用于采购会签）
  private final OrgHierarchyProvider org =
      new InMemoryOrgHierarchyProvider()
          .withLineManager(INITIATOR, "mgr1")
          .withLineManager("mgr1", "mgr2")
          .withLineManager("mgr2", "mgr3")
          .withDeptHead(INITIATOR, "head1");
  // 角色成员：财务 / 财务总监
  private final RoleMemberProvider roles =
      roleCode ->
          switch (roleCode) {
            case "finance" -> List.of("fin1");
            case "finance_director" -> List.of("fd1");
            default -> List.of();
          };

  /** 各单据的金额（驱动条件分支）。 */
  private final Map<String, Integer> amountByRecord =
      Map.of(
          "r-reimb-small", 800,
          "r-reimb-large", 5000,
          "r-reimb-nomgr", 800,
          "r-proc-small", 10000,
          "r-proc-large", 100000);

  @BeforeEach
  void setUp() throws Exception {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    mapper = context.getBean(ObjectMapper.class);
    outbox = context.getBean(WorkflowStatusOutboxRepository.class);
    notificationPort =
        new PersistenceNotificationPort(
            context.getBean(com.bone.workflow.starter.sdk.WfNotificationSdkRepository.class),
            () -> "N" + idSeq.incrementAndGet());

    seedReimbursement();
    seedProcurement();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  // ====================== 报销场景 ======================

  @Test
  @DisplayName("报销-小额(≤1000)：直属主管审批 → 直接通过，财务不介入")
  void reimbursement_small_approvedByManagerOnly() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("expense", "r-reimb-small", "reimbursement", INITIATOR);

    // 启动后停在直属主管节点，且主管收到站内信
    assertEquals("mgr", instance.currentNodeCode());
    List<WorkflowTask> pending = engine.listPendingTasks(instance.id());
    assertEquals(1, pending.size());
    assertEquals("mgr1", pending.get(0).assigneeId());
    assertInbox("mgr1", "TASK_CREATED", instance.id());

    // 主管同意 → 金额 800 不超阈 → 结束
    WorkflowInstance approved =
        engine.completeTask(pending.get(0).id(), Decision.APPROVE, "同意", "mgr1");
    assertEquals(Phase.APPROVED, approved.phase());
    assertEquals(0, engine.listPendingTasks(instance.id()).size());
    // 发起人收到终态通知；财务未介入故无待办
    assertInbox(INITIATOR, "TERMINATED", instance.id());
  }

  @Test
  @DisplayName("报销-大额(>1000)：直属主管 → 财务，两级串行审批")
  void reimbursement_large_twoLevelSerial() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("expense", "r-reimb-large", "reimbursement", INITIATOR);
    assertEquals("mgr", instance.currentNodeCode());

    // 主管审批
    WorkflowTask mgrTask = engine.listPendingTasks(instance.id()).get(0);
    instance = engine.completeTask(mgrTask.id(), Decision.APPROVE, "同意", "mgr1");
    assertEquals("finance", instance.currentNodeCode(), "大额必须再走财务");
    WorkflowTask finTask = engine.listPendingTasks(instance.id()).get(0);
    assertEquals("fin1", finTask.assigneeId());
    assertInbox("fin1", "TASK_CREATED", instance.id());

    // 财务审批 → 通过
    WorkflowInstance approved = engine.completeTask(finTask.id(), Decision.APPROVE, "合规", "fin1");
    assertEquals(Phase.APPROVED, approved.phase());
  }

  @Test
  @DisplayName("报销-组织数据缺失（无主管）：失败关闭 → 挂起 + 管理员告警，不放行")
  void reimbursement_noManager_suspendsFailClosed() {
    // 该 resolver 的组织目录里没有 INITIATOR 的主管 → LINE_MANAGER 解析不出人
    OrgHierarchyProvider emptyOrg = new InMemoryOrgHierarchyProvider();
    WorkflowEngine engine = engineWithOrg(emptyOrg);

    WorkflowInstance instance =
        engine.start("expense", "r-reimb-nomgr", "reimbursement", INITIATOR);

    assertEquals(Phase.SUSPENDED, instance.phase(), "解析不出审批人必须挂起，而非放行");
    assertTrue(engine.listPendingTasks(instance.id()).isEmpty(), "挂起节点不得生成幽灵待办");
    assertInbox("workflow-admin", "SUSPENDED", instance.id());
  }

  // ====================== 采购场景 ======================

  @Test
  @DisplayName("采购-小额(≤50000)：部门负责人审批 → 通过")
  void procurement_small_deptHeadOnly() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance = engine.start("purchase", "r-proc-small", "procurement", INITIATOR);
    assertEquals("dept", instance.currentNodeCode());

    WorkflowTask deptTask = engine.listPendingTasks(instance.id()).get(0);
    assertEquals("head1", deptTask.assigneeId(), "部门负责人审批");
    assertInbox("head1", "TASK_CREATED", instance.id());

    WorkflowInstance approved = engine.completeTask(deptTask.id(), Decision.APPROVE, "同意", "head1");
    assertEquals(Phase.APPROVED, approved.phase());
  }

  @Test
  @DisplayName("采购-大额(>50000)：部门负责人 → 财务总监 + 向上2级副总 会签（全员通过才通过）")
  void procurement_large_countersignAllMustApprove() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance = engine.start("purchase", "r-proc-large", "procurement", INITIATOR);

    // 部门负责人先批
    WorkflowTask deptTask = engine.listPendingTasks(instance.id()).get(0);
    instance = engine.completeTask(deptTask.id(), Decision.APPROVE, "同意", "head1");
    assertEquals("joint", instance.currentNodeCode());

    // 会签节点：财务总监(fd1) + 向上2级副总(mgr2)
    List<WorkflowTask> countersign = engine.listPendingTasks(instance.id());
    assertEquals(2, countersign.size());
    assertTrue(countersign.stream().anyMatch(t -> t.assigneeId().equals("fd1")));
    assertTrue(countersign.stream().anyMatch(t -> t.assigneeId().equals("mgr2")));
    assertInbox("fd1", "TASK_CREATED", instance.id());
    assertInbox("mgr2", "TASK_CREATED", instance.id());

    // 仅一人通过：会签未齐，仍停在节点
    WorkflowTask fdTask = countersign.get(0);
    WorkflowInstance afterOne =
        engine.completeTask(fdTask.id(), Decision.APPROVE, "合规", fdTask.assigneeId());
    assertEquals(Phase.RUNNING, afterOne.phase(), "会签未齐不得推进");
    assertEquals(1, engine.listPendingTasks(instance.id()).size(), "另一人会签人待办仍在");

    // 第二人通过：全员通过 → 推进到结束
    WorkflowTask vpTask = engine.listPendingTasks(instance.id()).get(0);
    WorkflowInstance approved =
        engine.completeTask(vpTask.id(), Decision.APPROVE, "同意", vpTask.assigneeId());
    assertEquals(Phase.APPROVED, approved.phase());
    assertEquals(0, engine.listPendingTasks(instance.id()).size());
  }

  // ====================== 工具 ======================

  private WorkflowEngine engineWithOrg(OrgHierarchyProvider orgProvider) {
    ApproverResolver resolver =
        new com.bone.workflow.starter.adapter.RecordFieldApproverResolver(roles, orgProvider);
    WorkflowRecordPort recordPort =
        (entityCode, recordId) -> Map.of("amount", amountByRecord.getOrDefault(recordId, 0));
    WorkflowStatusPort statusPort =
        new OutboxStatusPort(outbox, () -> "OBX" + idSeq.incrementAndGet());
    return new WorkflowEngineImpl(
        context.getBean(WorkflowDefinitionRepository.class),
        context.getBean(WorkflowInstanceRepository.class),
        resolver,
        recordPort,
        statusPort,
        noopAudit(),
        () -> "W" + idSeq.incrementAndGet(),
        new StructuredConditionEvaluator(),
        new WorkflowDefinitionValidator(),
        Clock.systemUTC(),
        notificationPort);
  }

  private void assertInbox(String recipient, String type, String instanceId) {
    List<WorkflowNotification> inbox = notificationPort.inbox(TENANT, recipient, false);
    boolean found =
        inbox.stream().anyMatch(n -> type.equals(n.type()) && instanceId.equals(n.instanceId()));
    assertTrue(found, "收件箱应包含 " + recipient + " 的 " + type + " 通知（instance=" + instanceId + "）");
  }

  private WorkflowAuditPort noopAudit() {
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

  // 报销：start → mgr(LINE_MANAGER) → cond(amount>1000 ? finance : end) → finance(ROLE finance) → end
  private void seedReimbursement() throws Exception {
    JsonNode overThreshold =
        mapper.readTree("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":1000}");
    WorkflowDefinition def =
        new WorkflowDefinition(
            "reimbursement",
            "报销审批",
            1,
            List.of(
                new Node("start", NodeType.START, null, "发起"),
                new Node("mgr", NodeType.APPROVE, ApprovalMode.SINGLE, "直属主管审批"),
                new Node("cond", NodeType.CONDITION, null, "金额分流"),
                new Node("finance", NodeType.APPROVE, ApprovalMode.SINGLE, "财务审批"),
                new Node("end", NodeType.END, null, "结束")),
            List.of(
                new Edge("start", "mgr", null, null),
                new Edge("mgr", "cond", null, null),
                new Edge("cond", "finance", overThreshold, "金额>1000"),
                new Edge("cond", "end", null, "默认"),
                new Edge("finance", "end", null, null)),
            Map.of(
                "mgr",
                List.of(new AssignmentRule("LINE_MANAGER", Map.of())),
                "finance",
                List.of(new AssignmentRule("ROLE", Map.of("roleCode", "finance")))));
    insertDefinition(def);
  }

  // 采购：start → dept(DEPT_HEAD) → cond(amount>50000 ? joint : end)
  //      joint(会签 ALL): 财务总监(ROLE) + 向上2级副总(LEVEL_UP level=2) → end
  private void seedProcurement() throws Exception {
    JsonNode bigAmount =
        mapper.readTree("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":50000}");
    WorkflowDefinition def =
        new WorkflowDefinition(
            "procurement",
            "采购审批",
            1,
            List.of(
                new Node("start", NodeType.START, null, "发起"),
                new Node("dept", NodeType.APPROVE, ApprovalMode.SINGLE, "部门负责人审批"),
                new Node("cond", NodeType.CONDITION, null, "金额分流"),
                new Node("joint", NodeType.APPROVE, ApprovalMode.ALL, "大额会签"),
                new Node("end", NodeType.END, null, "结束")),
            List.of(
                new Edge("start", "dept", null, null),
                new Edge("dept", "cond", null, null),
                new Edge("cond", "joint", bigAmount, "金额>50000"),
                new Edge("cond", "end", null, "默认"),
                new Edge("joint", "end", null, null)),
            Map.of(
                "dept",
                List.of(new AssignmentRule("DEPT_HEAD", Map.of())),
                "joint",
                List.of(
                    new AssignmentRule("ROLE", Map.of("roleCode", "finance_director")),
                    new AssignmentRule("LEVEL_UP", Map.of("level", 2)))));
    insertDefinition(def);
  }

  private void insertDefinition(WorkflowDefinition def) throws Exception {
    String defKey = def.key();
    jdbc.update(
        "INSERT INTO wf_process_def (id, tenant_id, def_key, def_name, def_version, status,"
            + " enabled, definition_json, created_by, created_at, updated_by, updated_at,"
            + " deleted, version)"
            + " VALUES (?, ?, ?, ?, 1, 'PUBLISHED', 1, ?, 'u1', NOW(), 'u1', NOW(), 0, 0)",
        "D-" + defKey,
        TENANT,
        defKey,
        def.name(),
        mapper.writeValueAsString(def));
  }
}
