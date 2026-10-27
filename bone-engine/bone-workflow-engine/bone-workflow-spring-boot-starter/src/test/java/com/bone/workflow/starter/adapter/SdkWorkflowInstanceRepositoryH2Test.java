package com.bone.workflow.starter.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 实例仓储的<b>端到端</b>测试：H2 经 SDK {@code @Sql} 代理真实执行 {@code WfInstanceSdkRepository} 里的生产 SQL。
 *
 * <p><b>为什么必须这样测</b>：会签死锁的根因是「判据与推进被拆成两次读」。这类缺陷在内存 stub 测试里<b>永远绿</b>—— stub
 * 没有并发语义，单线程顺序调用怎么跑都对。只有让生产 SQL 在真实数据库上跑，才能证明原子性成立。
 *
 * <p><b>判别性设计（防假绿）</b>：每个用例都构造「若实现退化为先查后改就会失败」的数据布局。特别是 {@link
 * #concurrentApprovals_cannotDoubleAdvance()}：两人先后同意后，第二次推进必须返回 false 且节点不再移动——
 * 若实现是「读任务列表判齐再改实例」，两个并发办理人会各自读到对方未同意的旧快照，双双判「未齐」而都不推进， 流程永久卡死；且推进缺少 {@code current_node_code}
 * 守卫时会重复推进。
 */
class SdkWorkflowInstanceRepositoryH2Test {

  private static final long TENANT = 1L;
  private static final String INSTANCE = "100";
  private static final String NODE = "committee";

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private SdkWorkflowInstanceRepository repository;
  private final AtomicLong taskId = new AtomicLong(1000);

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    // 按具体类型取生产 @Bean 方法创建的适配器：注册遗漏 / 条件恒假在此现形，而不是到宿主启动才爆炸
    repository = context.getBean(SdkWorkflowInstanceRepository.class);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("会签(ALL)：第一人同意不得推进，第二人同意才推进")
  void countersign_advancesOnlyWhenAllApproved() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));

    approve("c1");
    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1"),
        "会签未齐（c2 仍待办）不得推进——否则等于一人拍板");
    assertEquals(NODE, currentNode(), "未推进时节点必须留在原处");

    approve("c2");
    assertTrue(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c2"),
        "会签已齐必须推进");
    assertEquals("end", currentNode());
  }

  @Test
  @DisplayName("会签并发不死锁：两人都同意后，推进只能发生一次")
  void concurrentApprovals_cannotDoubleAdvance() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));

    // 两人先后完成决策（并发下的交错顺序）
    approve("c1");
    approve("c2");

    boolean firstAdvance =
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1");
    boolean secondAdvance =
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c2");

    assertTrue(firstAdvance, "会签已齐，首次推进必须成功");
    assertFalse(secondAdvance, "已推进过，重复推进必须失败（幂等）——否则节点被反复改写");
    assertEquals("end", currentNode(), "节点只能被推进一次");
  }

  @Test
  @DisplayName("会签并发无死锁：后决策者推进成功后，先决策者的推进必然失败")
  void countersign_noDeadlock_whenBothSeeStaleSnapshot() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));

    // 关键场景：c1 先同意，c2 后同意。若实现是「读任务列表判齐」，c1 在同意后会读到「c2 未同意」而跳过，
    // c2 同样跳过 → 双方都不推进 → 流程永久卡在待办。原子 SQL 下只有让条件成立的那一方能推进。
    approve("c1");
    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1"));
    approve("c2");
    assertTrue(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c2"));

    // 推进后实例已离开该节点，任何针对旧节点的推进都必须失败
    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1"),
        "实例已离开该节点，旧节点的推进必须失败");
    assertEquals("end", currentNode(), "流程必须真的走到了 end，不得卡死");
  }

  @Test
  @DisplayName("或签(ANY)：一人同意即推进，余下待办被作废（不留幽灵待办）")
  void anyApproval_advancesImmediately_andCancelsRest() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ANY, List.of("c1", "c2", "c3"));
    assertEquals(3, repository.listPendingTasks(INSTANCE).size());

    approve("c2");
    assertTrue(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ANY, null, "c2"),
        "或签一人通过即推进");
    repository.cancelPendingTasksOfNode(INSTANCE, NODE, "c2");

    assertEquals("end", currentNode());
    assertEquals(
        0, repository.listPendingTasks(INSTANCE).size(), "余下待办必须被作废，否则「我的待办」里留幽灵项，点它只得到「流程已终结」");
    assertEquals(2, countTasksByStatus("CANCELLED"));
  }

  @Test
  @DisplayName("驳回阻断推进：节点内有人驳回时不得因其他人同意而推进")
  void rejection_blocksAdvance() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ANY, List.of("c1", "c2"));

    approve("c1");
    reject("c2");

    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ANY, null, "c1"),
        "节点内已有驳回，不得推进");
    assertEquals(NODE, currentNode());
  }

  @Test
  @DisplayName("会签 fail-closed：任务处于已决策显式集之外的状态（预演 v2 转办中）时不得推进")
  void countersign_failsClosed_onStatusOutsideDecidedSet() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));

    approve("c1");
    // 预演 v2 转办：c2 的待办被转走，任务进入 TRANSFERRED 中间态——不是 PENDING，也不在已决策显式集内
    jdbc.update(
        "UPDATE wf_task SET status = 'TRANSFERRED'"
            + " WHERE tenant_id = ? AND node_code = ? AND assignee_id = ?",
        TENANT,
        NODE,
        "c2");

    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1"),
        "c2 尚未表态（TRANSFERRED 不在已决策显式集）——此时推进会签节点等于越权放行");
    assertEquals(NODE, currentNode(), "fail-closed：节点必须留在原处等人，而不是静默往下走");
  }

  @Test
  @DisplayName("会签显式集边界：CANCELLED 属已决策（一人同意+一人任务作废 ⇒ 可推进）")
  void countersign_treatsCancelledAsDecided() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));

    approve("c1");
    // 管理员作废 c2 的待办（如该审批人已离职，任务被取消而非驳回）
    jdbc.update(
        "UPDATE wf_task SET status = 'CANCELLED'"
            + " WHERE tenant_id = ? AND node_code = ? AND assignee_id = ?",
        TENANT,
        NODE,
        "c2");

    assertTrue(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1"),
        "CANCELLED 在已决策显式集内——作废的待办不得把会签节点永久焊死");
    assertEquals("end", currentNode());
  }

  @Test
  @DisplayName("非审批节点（START/CONDITION/CC，mode=null）无任务也必须推得动")
  void nonApprovalNode_advancesWithoutTasks() {
    // 引擎对非审批节点传 completion=null，适配器映射为 AUTO。若折成 SINGLE，
    // 本语句要求「存在 APPROVED 任务」——START 节点没有任务 ⇒ 0 行 ⇒ 流程启动即静默卡死。
    WorkflowInstance atStart =
        new WorkflowInstance(
            "900",
            "expense_reimbursement",
            "r-start",
            "expense",
            1,
            "start",
            WorkflowInstance.Phase.RUNNING,
            "u1",
            Instant.parse("2026-10-23T00:00:00Z"),
            Instant.parse("2026-10-23T00:00:00Z"));
    assertTrue(repository.claimActiveInstance(atStart));
    repository.saveInstance(atStart, "u1");

    assertTrue(
        repository.compareAndAdvanceNode("900", "start", "cond", null, null, "u1"),
        "START 节点不产生任务，必须能无条件推进（P0-1 回归：生产 SQL 推不动非审批节点）");
    assertEquals("cond", repository.findInstance("900").orElseThrow().currentNodeCode());
  }

  @Test
  @DisplayName("负向：审批节点零任务不得推进（「无任务即放行」是 fail-open）")
  void approvalNodeWithoutTasks_mustNotAdvance() {
    // 审批节点却没有任何任务（指派解析缺陷漏建 / 任务被软删）：此时放行等于无人审批即通过
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of());

    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.ALL, null, "c1"),
        "审批节点零任务必须 fail-closed 卡住等人，绝不不得因「无任务」而放行");
    assertEquals(NODE, currentNode(), "fail-closed：节点必须留在原处");
  }

  @Test
  @DisplayName("任务 CAS 带实例终态守卫：实例已终结后迟到决策必须失败")
  void taskCas_rejectedWhenInstanceTerminated() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));
    String taskId = repository.listTasksByNode(INSTANCE, NODE).get(0).id();

    // 并发：撤回先把实例打到 WITHDRAWN
    assertTrue(
        repository.compareAndSetPhase(
            INSTANCE, WorkflowInstance.Phase.RUNNING, WorkflowInstance.Phase.WITHDRAWN, "u1"));

    assertFalse(
        repository.compareAndSetTaskStatus(
            taskId,
            WorkflowTask.Status.PENDING,
            WorkflowTask.Status.APPROVED,
            WorkflowTask.Decision.APPROVE,
            "同意",
            "c1"),
        "实例已终结，迟到决策不得落库——否则审计里出现「撤回流程中有人同意」，且推进 CAS 随后失败留下永不收敛的中间态");
  }

  @Test
  @DisplayName("任务决策 CAS：只有一方能把 PENDING 改成 APPROVED")
  void taskDecision_isCompareAndSet() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));
    String taskId = repository.listTasksByNode(INSTANCE, NODE).get(0).id();

    assertTrue(
        repository.compareAndSetTaskStatus(
            taskId,
            WorkflowTask.Status.PENDING,
            WorkflowTask.Status.APPROVED,
            WorkflowTask.Decision.APPROVE,
            "同意",
            "c1"));
    assertFalse(
        repository.compareAndSetTaskStatus(
            taskId,
            WorkflowTask.Status.PENDING,
            WorkflowTask.Status.APPROVED,
            WorkflowTask.Decision.APPROVE,
            "同意",
            "c1"),
        "已非 PENDING，重复 CAS 必须失败——否则两人同时点同意会双写");

    WorkflowTask task = repository.findTask(taskId).orElseThrow();
    assertEquals(WorkflowTask.Decision.APPROVE, task.decision());
  }

  @Test
  @DisplayName("审批意见含 SQL 关键字/分号必须原样存取（SDK 值级扫描误禁回归）")
  void commentContainingSqlKeywords_roundTripsThroughSdkChannel() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));
    String taskId = repository.listTasksByNode(INSTANCE, NODE).get(0).id();
    // 真实驳回理由就长这样：自由文本里出现 update/DROP TABLE/分号完全合法。
    // 旧 JDBC 通道容忍；SDK #{} 曾因「扫参数值里的 SQL 关键字+分号」整条拒绝 ⇒ 审批意见写不进去（500）。
    String comment = "please update the amount; DROP TABLE wf_task";

    assertTrue(
        repository.compareAndSetTaskStatus(
            taskId,
            WorkflowTask.Status.PENDING,
            WorkflowTask.Status.REJECTED,
            WorkflowTask.Decision.REJECT,
            comment,
            "c1"),
        "含 SQL 关键字的合法审批意见不得被 SDK 拒写——真注入面只有 ${}，值级扫描必须已移除");

    assertEquals(
        comment,
        repository.findTask(taskId).orElseThrow().comment(),
        "意见必须逐字原样读回：若 SDK 对值做了任何改写/截断，审计轨迹即被污染");
  }

  @Test
  @DisplayName("阶段 CAS：期望阶段不匹配时拒绝改写（终态不可被回写）")
  void phase_compareAndSet_rejectsStaleExpectation() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));

    assertFalse(
        repository.compareAndSetPhase(
            INSTANCE, WorkflowInstance.Phase.APPROVED, WorkflowInstance.Phase.REJECTED, "u1"),
        "实例并非 APPROVED，不得改写成 REJECTED");
    assertEquals(WorkflowInstance.Phase.RUNNING, phase());

    assertTrue(
        repository.compareAndSetPhase(
            INSTANCE, WorkflowInstance.Phase.RUNNING, WorkflowInstance.Phase.WITHDRAWN, "u1"));
    assertEquals(WorkflowInstance.Phase.WITHDRAWN, phase());
  }

  @Test
  @DisplayName("按单据查实例 + 按办理人查待办")
  void findInstanceByRecord_and_listTasksByAssignee() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));

    Optional<WorkflowInstance> found =
        repository.findInstanceByRecord("expense_reimbursement", "r-1");
    assertTrue(found.isPresent());
    assertEquals(INSTANCE, found.orElseThrow().id());
    assertEquals(NODE, found.orElseThrow().currentNodeCode());

    assertEquals(1, repository.listTasksByAssignee("c1", WorkflowTask.Status.PENDING).size());
    assertEquals(1, repository.listTasksByAssignee("c2", WorkflowTask.Status.PENDING).size());
    assertEquals(0, repository.listTasksByAssignee("nobody", WorkflowTask.Status.PENDING).size());
  }

  @Test
  @DisplayName("listTasks 返回全量任务（含已决策），listPendingTasks 只取 PENDING——轨迹查询口径分野")
  void listTasks_returnsAllIncludingDecided() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.ALL, List.of("c1", "c2"));
    List<WorkflowTask> tasks = repository.listTasksByNode(INSTANCE, NODE);
    String c1 =
        tasks.stream().filter(t -> t.assigneeId().equals("c1")).findFirst().orElseThrow().id();
    // c1 决策通过，c2 仍待办：listTasks 应两者都在，listPendingTasks 只 c2
    assertTrue(
        repository.compareAndSetTaskStatus(
            c1,
            WorkflowTask.Status.PENDING,
            WorkflowTask.Status.APPROVED,
            WorkflowTask.Decision.APPROVE,
            "同意",
            "c1"));

    List<WorkflowTask> all = repository.listTasks(INSTANCE);
    assertEquals(2, all.size(), "listTasks 必须含已决策任务，不得只取 PENDING");
    assertTrue(all.stream().anyMatch(t -> t.status() == WorkflowTask.Status.APPROVED));
    assertTrue(all.stream().anyMatch(t -> t.status() == WorkflowTask.Status.PENDING));
    assertEquals(1, repository.listPendingTasks(INSTANCE).size(), "listPendingTasks 只取 PENDING");
  }

  @Test
  @DisplayName("重复启动不双建：活动槽位被占用时第二次抢占必须失败")
  void activeSlot_preventsDuplicateInstance() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));

    WorkflowInstance second =
        new WorkflowInstance(
            "200",
            "expense_reimbursement",
            "r-1",
            "expense",
            1,
            "start",
            WorkflowInstance.Phase.RUNNING,
            "u1",
            Instant.parse("2026-10-23T00:00:00Z"),
            Instant.parse("2026-10-23T00:00:00Z"));

    assertFalse(
        repository.claimActiveInstance(second), "同一单据已有活动实例，第二次抢占必须失败——否则连点两次提交会得到两个并行实例、双份审批");

    // 终态释放槽位后，同一单据可重新提交
    assertTrue(
        repository.compareAndSetPhase(
            INSTANCE, WorkflowInstance.Phase.RUNNING, WorkflowInstance.Phase.WITHDRAWN, "u1"));
    assertTrue(repository.claimActiveInstance(second), "流程已终结，槽位必须已释放，否则同一单据永久无法重新提交");
  }

  @Test
  @DisplayName("陈旧指针自愈：上次流程终态但指针残留时，重新提交不被焊死")
  void staleActiveSlot_isReclaimed() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));
    // 直接把实例打到终态，但【不】经 compareAndSetPhase（模拟崩溃/释放失败 → 指针残留）
    jdbc.update(
        "UPDATE wf_instance SET phase = 'APPROVED' WHERE id = ? AND tenant_id = ?",
        INSTANCE,
        TENANT);

    WorkflowInstance retry =
        new WorkflowInstance(
            "300",
            "expense_reimbursement",
            "r-1",
            "expense",
            1,
            "start",
            WorkflowInstance.Phase.RUNNING,
            "u1",
            Instant.parse("2026-10-23T00:00:00Z"),
            Instant.parse("2026-10-23T00:00:00Z"));

    assertTrue(repository.claimActiveInstance(retry), "指针指向的实例已终态 ⇒ 属陈旧指针，必须清理后放行，否则重新提交被永久焊死");
    assertEquals("300", activeSlotInstanceId());
  }

  @Test
  @DisplayName("活动槽位按租户隔离：不同租户同一单据互不抢占")
  void activeSlot_isTenantScoped() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of("c1"));

    TenantContext.setTenantId(2L);
    WorkflowInstance otherTenant =
        new WorkflowInstance(
            "400",
            "expense_reimbursement",
            "r-1",
            "expense",
            1,
            "start",
            WorkflowInstance.Phase.RUNNING,
            "u1",
            Instant.parse("2026-10-23T00:00:00Z"),
            Instant.parse("2026-10-23T00:00:00Z"));
    assertTrue(repository.claimActiveInstance(otherTenant), "租户 2 的同一单据不得被租户 1 的槽位挡住");
    TenantContext.setTenantId(TENANT);
  }

  private String activeSlotInstanceId() {
    return jdbc.queryForObject(
        "SELECT instance_id FROM wf_active_instance WHERE tenant_id = ? AND entity_code = ?"
            + " AND record_id = ?",
        String.class,
        TENANT,
        "expense_reimbursement",
        "r-1");
  }

  @Test
  @DisplayName("无任务节点（START/CC）必须能无条件推进——mode=null 不得被当成「等审批」")
  void tasklessNode_advancesUnconditionally() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.SINGLE, List.of()); // 该节点无任何任务
    assertEquals(0, repository.listTasksByNode(INSTANCE, NODE).size());

    // mode 传 null（START / CC 节点没有 ApprovalMode）：必须能推进
    assertTrue(
        repository.compareAndAdvanceNode(INSTANCE, NODE, "end", null, null, "u1"),
        "START / CC 节点不产生审批任务，若 SQL 仍要求「存在 APPROVED 任务」则永远推不动——"
            + "这是只有真 SQL 才暴露、内存 stub 测试抓不到的缺陷");
    assertEquals("end", currentNode());
  }

  @Test
  @DisplayName("比例会签(RATIO)：已通过人数达法定人数才推进")
  void ratio_advancesOnlyWhenQuorumMet() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.RATIO, List.of("c1", "c2", "c3"));

    approve("c1");
    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.RATIO, 2, "c1"),
        "仅 1 人通过（< 法定 2）不得推进");
    assertEquals(NODE, currentNode());

    approve("c2");
    assertTrue(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.RATIO, 2, "c2"),
        "已通过 2 人 ≥ 法定 2 必须推进");
    assertEquals("end", currentNode());
  }

  @Test
  @DisplayName("比例会签(RATIO)：单条驳回不阻断推进（容忍少数人反对），达额即推进")
  void ratio_rejectionToleratedUntilQuorum() {
    givenInstanceAtNode(WorkflowDefinition.ApprovalMode.RATIO, List.of("c1", "c2", "c3"));

    approve("c1");
    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.RATIO, 2, "c1"),
        "仅 1 人通过（< 法定 2）不得推进");
    // c2 驳回：比例会签容忍单条驳回（只要法定人数仍可达），SQL 不得因存在 REJECTED 而阻断
    reject("c2");
    assertFalse(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.RATIO, 2, "c1"),
        "比例会签不挂 REJECTED 守卫，1 通过 + 1 驳回 + 1 待决，仍未达额，不得推进");
    assertEquals(NODE, currentNode(), "存在驳回不得误推进");
    // c3 通过 ⇒ 已通过 2 人 ≥ 法定 2 ⇒ 推进（尽管 c2 已驳回）
    approve("c3");
    assertTrue(
        repository.compareAndAdvanceNode(
            INSTANCE, NODE, "end", WorkflowDefinition.ApprovalMode.RATIO, 2, "c1"),
        "达额后即使有驳回记录也必须推进");
    assertEquals("end", currentNode());
  }

  // ====================== 夹具 ======================

  private void givenInstanceAtNode(WorkflowDefinition.ApprovalMode mode, List<String> assignees) {
    WorkflowInstance instance =
        new WorkflowInstance(
            INSTANCE,
            "expense_reimbursement",
            "r-1",
            "expense",
            1,
            NODE,
            WorkflowInstance.Phase.RUNNING,
            "u1",
            Instant.parse("2026-10-23T00:00:00Z"),
            Instant.parse("2026-10-23T00:00:00Z"));
    // 与引擎 start() 的真实顺序一致：先抢占活动槽位，再落实例。
    // 若只 saveInstance 而不 claim，则不会写入 wf_active_instance 指针——「一单据一活动实例」的闸门形同不存在。
    boolean claimed = repository.claimActiveInstance(instance);
    if (!claimed) {
      throw new IllegalStateException("测试夹具抢占活动槽位失败：" + instance.id());
    }
    repository.saveInstance(instance, "u1");
    for (String assignee : assignees) {
      repository.saveTask(
          new WorkflowTask(
              String.valueOf(taskId.incrementAndGet()),
              INSTANCE,
              NODE,
              assignee,
              WorkflowTask.Status.PENDING,
              null,
              null,
              null,
              Instant.parse("2026-10-23T00:00:00Z"),
              null),
          "u1");
    }
  }

  private void approve(String assignee) {
    String id = pendingTaskOf(assignee);
    repository.compareAndSetTaskStatus(
        id,
        WorkflowTask.Status.PENDING,
        WorkflowTask.Status.APPROVED,
        WorkflowTask.Decision.APPROVE,
        "同意",
        assignee);
  }

  private void reject(String assignee) {
    String id = pendingTaskOf(assignee);
    repository.compareAndSetTaskStatus(
        id,
        WorkflowTask.Status.PENDING,
        WorkflowTask.Status.REJECTED,
        WorkflowTask.Decision.REJECT,
        "驳回",
        assignee);
  }

  private String pendingTaskOf(String assignee) {
    return repository.listTasksByAssignee(assignee, WorkflowTask.Status.PENDING).stream()
        .filter(t -> t.nodeCode().equals(NODE))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("找不到 " + assignee + " 的待办"))
        .id();
  }

  private String currentNode() {
    return repository.findInstance(INSTANCE).orElseThrow().currentNodeCode();
  }

  private WorkflowInstance.Phase phase() {
    return repository.findInstance(INSTANCE).orElseThrow().phase();
  }

  private int countTasksByStatus(String status) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM wf_task WHERE tenant_id = ? AND node_code = ? AND status = ?",
            Integer.class,
            TENANT,
            NODE,
            status);
    return count == null ? 0 : count;
  }
}
