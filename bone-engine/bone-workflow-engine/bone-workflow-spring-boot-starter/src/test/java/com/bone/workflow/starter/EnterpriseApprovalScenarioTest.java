package com.bone.workflow.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowInstance.Phase;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.domain.WorkflowTask.Decision;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowNotification;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.adapter.PersistenceNotificationPort;
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

  /**
   * 各单据的金额（驱动条件分支）。
   *
   * <p>用<b>可变</b> map 是为了验证「挂起 → 修数据 → 恢复」的闭合：分支歧义挂起后改金额再 {@code resume}， 流程应能续跑（见 {@link
   * #branchAmbiguity_recoversAfterDataFix}）。
   */
  private final Map<String, Integer> amountByRecord =
      new java.util.HashMap<>(
          Map.of(
              "r-reimb-small", 800,
              "r-reimb-large", 5000,
              "r-reimb-nomgr", 800,
              "r-proc-small", 10000,
              "r-proc-large", 100000,
              "r-reimb-cc", 800,
              "r-proc-overlap", 10000));

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
    seedReimbursementWithCc();
    seedOverlapProcurement();
    seedSelfApproval();
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
  @DisplayName("报销-驳回退回指定节点：财务驳回退回主管，主管重新审批后续跑至通过（无需另起新单）")
  void reimbursement_rejectToNode_resumesFlow() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("expense", "r-reimb-large", "reimbursement", INITIATOR);
    assertEquals("mgr", instance.currentNodeCode());

    // 主管通过 → 走到财务
    WorkflowTask mgrTask = engine.listPendingTasks(instance.id()).get(0);
    instance = engine.completeTask(mgrTask.id(), Decision.APPROVE, "同意", "mgr1");
    assertEquals("finance", instance.currentNodeCode());
    WorkflowTask finTask = engine.listPendingTasks(instance.id()).get(0);
    assertEquals("fin1", finTask.assigneeId());

    // 财务驳回，退回主管节点（而非整单驳回）——发起人不需另起新单，据意见修改后继续
    WorkflowInstance rejected =
        engine.completeTask(finTask.id(), Decision.REJECT, "发票不合规，请重提", "fin1", "mgr");
    assertEquals(Phase.RUNNING, rejected.phase(), "退回节点后流程仍在运行");
    assertEquals("mgr", rejected.currentNodeCode(), "currentNodeCode 应跳回主管节点");
    WorkflowTask reMgrTask = engine.listPendingTasks(instance.id()).get(0);
    assertEquals("mgr1", reMgrTask.assigneeId(), "主管节点应重新派单");
    assertInbox("mgr1", "TASK_CREATED", instance.id());

    // 主管重新审批通过 → 财务再审批 → 流程通过（退回通道闭环）
    instance = engine.completeTask(reMgrTask.id(), Decision.APPROVE, "已重提", "mgr1");
    assertEquals("finance", instance.currentNodeCode());
    WorkflowTask finTask2 = engine.listPendingTasks(instance.id()).get(0);
    WorkflowInstance approved = engine.completeTask(finTask2.id(), Decision.APPROVE, "合规", "fin1");
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

  @Test
  @DisplayName("报销-转办：主管把待办转交同级另一主管，原任务置 TRANSFERRED，后者审批后链路继续")
  void reimbursement_transferToPeer() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("expense", "r-reimb-large", "reimbursement", INITIATOR);
    WorkflowTask mgrTask = engine.listPendingTasks(instance.id()).get(0);
    assertEquals("mgr1", mgrTask.assigneeId());

    // 主管把待办转交同级 mgrPeer（本人视角转办，原任务留痕为 TRANSFERRED）
    WorkflowTask transferred =
        engine.transferTask(mgrTask.id(), "mgrPeer", "mgr1", "我休假，转给 mgrPeer");
    assertEquals("mgrPeer", transferred.assigneeId());
    assertEquals(WorkflowTask.Status.PENDING, transferred.status());
    // 原任务已不在待办列表，且历史中标记为 TRANSFERRED
    assertTrue(
        engine.listPendingTasks(instance.id()).stream().noneMatch(t -> t.id().equals(mgrTask.id())),
        "原任务不该再出现在待办");
    WorkflowTask original =
        engine.listInstanceHistory(instance.id()).stream()
            .filter(t -> t.id().equals(mgrTask.id()))
            .findFirst()
            .orElseThrow();
    assertEquals(WorkflowTask.Status.TRANSFERRED, original.status(), "原任务应留痕为已转交");
    assertInbox("mgrPeer", "TASK_CREATED", instance.id());

    // mgrPeer 审批 → 仍走财务（转办不改变审批链）
    instance = engine.completeTask(transferred.id(), Decision.APPROVE, "同意", "mgrPeer");
    assertEquals("finance", instance.currentNodeCode());
    WorkflowTask finTask = engine.listPendingTasks(instance.id()).get(0);
    WorkflowInstance approved = engine.completeTask(finTask.id(), Decision.APPROVE, "合规", "fin1");
    assertEquals(Phase.APPROVED, approved.phase());
  }

  @Test
  @DisplayName("采购-加签：会签节点上财务总监加签同事，被加签人须一并通过才通过")
  void procurement_addsignRequiresCountersign() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance = engine.start("purchase", "r-proc-large", "procurement", INITIATOR);
    WorkflowTask deptTask = engine.listPendingTasks(instance.id()).get(0);
    instance = engine.completeTask(deptTask.id(), Decision.APPROVE, "同意", "head1");
    assertEquals("joint", instance.currentNodeCode());

    List<WorkflowTask> countersign = engine.listPendingTasks(instance.id());
    assertEquals(2, countersign.size());
    WorkflowTask fdTask =
        countersign.stream().filter(t -> t.assigneeId().equals("fd1")).findFirst().orElseThrow();

    // 财务总监加签同事 fd2（同节点新增一条 PENDING，会签口径下须其一并通过）
    WorkflowTask signed = engine.addSign(fdTask.id(), "fd2", "fd1", "请 fd2 一并审核");
    assertEquals("fd2", signed.assigneeId());
    assertEquals(3, engine.listPendingTasks(instance.id()).size(), "加签后本节点共 3 个待办");
    assertInbox("fd2", "TASK_CREATED", instance.id());

    // fd1 通过：会签未齐（还差 mgr2、fd2）
    WorkflowInstance afterFd = engine.completeTask(fdTask.id(), Decision.APPROVE, "合规", "fd1");
    assertEquals(Phase.RUNNING, afterFd.phase());
    assertEquals(2, engine.listPendingTasks(instance.id()).size());

    // fd2 通过：仍差 mgr2
    WorkflowTask fd2Task =
        engine.listPendingTasks(instance.id()).stream()
            .filter(t -> t.assigneeId().equals("fd2"))
            .findFirst()
            .orElseThrow();
    instance = engine.completeTask(fd2Task.id(), Decision.APPROVE, "同意", "fd2");
    assertEquals(Phase.RUNNING, instance.phase());
    assertEquals(1, engine.listPendingTasks(instance.id()).size());

    // mgr2 通过：全员通过 → 结束
    WorkflowTask vpTask = engine.listPendingTasks(instance.id()).get(0);
    WorkflowInstance approved = engine.completeTask(vpTask.id(), Decision.APPROVE, "同意", "mgr2");
    assertEquals(Phase.APPROVED, approved.phase());
  }

  @Test
  @DisplayName("协作指令守卫：非办理人不可转办/加签；本节点已有待办不可重复加签")
  void collaborationGuards() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("expense", "r-reimb-large", "reimbursement", INITIATOR);
    WorkflowTask mgrTask = engine.listPendingTasks(instance.id()).get(0);

    // 非办理人（fin1）试图转办 → 拒绝
    assertThrows(
        WorkflowEngineException.class,
        () -> engine.transferTask(mgrTask.id(), "mgrPeer", "fin1", "越权"));

    // 加签目标已是本节点待办人（mgr1 给自己加签）→ 重复被拒
    assertThrows(
        WorkflowEngineException.class, () -> engine.addSign(mgrTask.id(), "mgr1", "mgr1", "自加签"));

    // 合法加签：mgr1 加签 mgrPeer（非待办人）→ 成功，且本节点变为 2 个待办
    WorkflowTask signed = engine.addSign(mgrTask.id(), "mgrPeer", "mgr1", "请协同");
    assertEquals("mgrPeer", signed.assigneeId());
    assertEquals(2, engine.listPendingTasks(instance.id()).size());

    // 重复加签同一人 → 拒绝
    assertThrows(
        WorkflowEngineException.class, () -> engine.addSign(mgrTask.id(), "mgrPeer", "mgr1", "重复"));
  }

  @Test
  @DisplayName("抄送节点：知会抄送人但不建待办、不阻塞流程")
  void cc_notifiesRecipientsWithoutCreatingTasks() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance = engine.start("expense", "r-reimb-cc", "reimb_cc", INITIATOR);

    // 先停在主管节点
    WorkflowTask mgrTask = engine.listPendingTasks(instance.id()).get(0);
    assertEquals("mgr1", mgrTask.assigneeId());

    // 主管同意 → 经过抄送节点（知会财务）→ 直接结束
    WorkflowInstance approved = engine.completeTask(mgrTask.id(), Decision.APPROVE, "同意", "mgr1");
    assertEquals(Phase.APPROVED, approved.phase(), "抄送不阻塞，应直接走完");

    // 抄送人收到 CC 知会，但不产生任何待办
    assertInbox("fin1", "CC", instance.id());
    assertTrue(
        engine.listTasksByAssignee("fin1", WorkflowTask.Status.PENDING).isEmpty(),
        "抄送不得生成待办（否则会签判据会把抄送人算进未决策集合）");
    assertEquals(0, engine.listPendingTasks(instance.id()).size());
  }

  @Test
  @DisplayName("分支歧义（两条分支同时命中）：失败关闭 → 挂起 + 管理员告警 + 稳定错误码，不留静默卡单")
  void branchAmbiguity_suspendsAndAlerts() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("purchase", "r-proc-overlap", "proc_overlap", INITIATOR);

    // 部门负责人同意 → 推进到条件节点，此时两条重叠分支（amount>1000 与 amount>5000）
    // 在 amount=10000 下同时命中 ⇒ 分支不可裁决
    WorkflowTask deptTask = engine.listPendingTasks(instance.id()).get(0);
    WorkflowEngineException failure =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(deptTask.id(), Decision.APPROVE, "同意", "head1"),
            "分支歧义必须抛引擎异常（宿主可按 code.httpStatus() 翻译），而非 IllegalStateException");
    assertEquals(
        com.bone.workflow.domain.WorkflowErrorCode.BRANCH_AMBIGUOUS,
        failure.code(),
        "错误码应为 BRANCH_AMBIGUOUS");

    // 关键：不得留下「停在条件节点、无待办、无告警」的静默死单 —— 必须挂起并告警
    WorkflowInstance latest = engine.findInstanceById(instance.id()).orElseThrow();
    assertEquals(Phase.SUSPENDED, latest.phase(), "分支不可裁决必须挂起（fail-closed）");
    assertInbox("workflow-admin", "SUSPENDED", instance.id());
  }

  @Test
  @DisplayName("分支歧义修复后：发起人对条件节点挂起的实例 resume 可续跑（挂起→恢复通道闭合）")
  void branchAmbiguity_recoversAfterDataFix() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("purchase", "r-proc-overlap", "proc_overlap", INITIATOR);
    WorkflowTask deptTask = engine.listPendingTasks(instance.id()).get(0);
    assertThrows(
        WorkflowEngineException.class,
        () -> engine.completeTask(deptTask.id(), Decision.APPROVE, "同意", "head1"));
    assertEquals(Phase.SUSPENDED, engine.findInstanceById(instance.id()).orElseThrow().phase());

    // 修复数据：金额改为 800，两条分支都不命中 ⇒ 走默认边直接结束
    amountByRecord.put("r-proc-overlap", 800);
    WorkflowInstance resumed = engine.resume(instance.id(), INITIATOR);
    assertEquals(
        Phase.APPROVED, resumed.phase(), "条件节点挂起必须可恢复（否则 resume 会去解析不存在的审批人而永远 NO_ASSIGNEE）");
  }

  // 自审批（内控红线）：start → mgr(USER u1=发起人本人) → end
  private void seedSelfApproval() throws Exception {
    WorkflowDefinition def =
        new WorkflowDefinition(
            "reimb_self",
            "报销审批(自审批)",
            1,
            List.of(
                new Node("start", NodeType.START, null, "发起"),
                new Node("mgr", NodeType.APPROVE, ApprovalMode.SINGLE, "主管审批"),
                new Node("end", NodeType.END, null, "结束")),
            List.of(new Edge("start", "mgr", null, null), new Edge("mgr", "end", null, null)),
            // 指派给发起人本人：建模错误或组织数据脏时会出现，内控上必须拦住
            Map.of("mgr", List.of(new AssignmentRule("USER", Map.of("userId", INITIATOR)))));
    insertDefinition(def);
  }

  // ====================== 内控与可观测（2026-10-27） ======================

  @Test
  @DisplayName("报销-自审批（审批人=发起人）：失败关闭 → 挂起 + 管理员告警，不派单给发起人")
  void reimbursement_selfApproval_suspendsFailClosed() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance = engine.start("expense", "r-reimb-self", "reimb_self", INITIATOR);

    assertEquals(Phase.SUSPENDED, instance.phase(), "自审批不得放行——否则这道审批事实上不存在");
    assertTrue(engine.listPendingTasks(instance.id()).isEmpty(), "不得向发起人派发审批待办");
    assertInbox("workflow-admin", "SUSPENDED", instance.id());
  }

  @Test
  @DisplayName("撤回：其余办理人的待办被作废且收到站内知会（待办不得凭空消失）")
  void withdrawal_notifiesCancelledApprover() {
    WorkflowEngine engine = engineWithOrg(org);
    WorkflowInstance instance =
        engine.start("expense", "r-reimb-small", "reimbursement", INITIATOR);
    WorkflowTask mgrTask = engine.listPendingTasks(instance.id()).get(0);
    String approver = mgrTask.assigneeId();

    engine.withdraw(instance.id(), INITIATOR);

    assertEquals(Phase.WITHDRAWN, engine.findInstanceById(instance.id()).orElseThrow().phase());
    assertInbox(approver, "TASK_CANCELLED", instance.id());
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

      @Override
      public void recordTransfer(
          WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}

      @Override
      public void recordAddSign(
          WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}
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

  // 报销（含抄送）：start → mgr(LINE_MANAGER) → cc(ROLE finance 知会) → end
  private void seedReimbursementWithCc() throws Exception {
    WorkflowDefinition def =
        new WorkflowDefinition(
            "reimb_cc",
            "报销审批(含抄送)",
            1,
            List.of(
                new Node("start", NodeType.START, null, "发起"),
                new Node("mgr", NodeType.APPROVE, ApprovalMode.SINGLE, "直属主管审批"),
                new Node("cc", NodeType.CC, null, "知会财务"),
                new Node("end", NodeType.END, null, "结束")),
            List.of(
                new Edge("start", "mgr", null, null),
                new Edge("mgr", "cc", null, null),
                new Edge("cc", "end", null, null)),
            Map.of(
                "mgr",
                List.of(new AssignmentRule("LINE_MANAGER", Map.of())),
                "cc",
                List.of(new AssignmentRule("ROLE", Map.of("roleCode", "finance")))));
    insertDefinition(def);
  }

  // 采购（条件重叠，用于验证分支歧义）：start → dept(DEPT_HEAD)
  //   → cond(amount>1000 → finance / amount>5000 → vp / 默认 → end)
  //   两条分支目标不同（否则先撞「重复边 WF-011」），但在 amount>5000 时会<b>同时命中</b>
  private void seedOverlapProcurement() throws Exception {
    JsonNode overThousand =
        mapper.readTree("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":1000}");
    JsonNode overFiveThousand =
        mapper.readTree("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}");
    WorkflowDefinition def =
        new WorkflowDefinition(
            "proc_overlap",
            "采购审批(条件重叠)",
            1,
            List.of(
                new Node("start", NodeType.START, null, "发起"),
                new Node("dept", NodeType.APPROVE, ApprovalMode.SINGLE, "部门负责人审批"),
                new Node("cond", NodeType.CONDITION, null, "金额分流"),
                new Node("finance", NodeType.APPROVE, ApprovalMode.SINGLE, "财务审批"),
                new Node("vp", NodeType.APPROVE, ApprovalMode.SINGLE, "副总审批"),
                new Node("end", NodeType.END, null, "结束")),
            List.of(
                new Edge("start", "dept", null, null),
                new Edge("dept", "cond", null, null),
                new Edge("cond", "finance", overThousand, "金额>1000"),
                new Edge("cond", "vp", overFiveThousand, "金额>5000"),
                new Edge("cond", "end", null, "默认"),
                new Edge("finance", "end", null, null),
                new Edge("vp", "end", null, null)),
            Map.of(
                "dept",
                List.of(new AssignmentRule("DEPT_HEAD", Map.of())),
                "finance",
                List.of(new AssignmentRule("ROLE", Map.of("roleCode", "finance"))),
                "vp",
                List.of(new AssignmentRule("LEVEL_UP", Map.of("level", 2)))));
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
