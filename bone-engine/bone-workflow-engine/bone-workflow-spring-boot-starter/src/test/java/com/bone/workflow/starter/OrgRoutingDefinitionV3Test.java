package com.bone.workflow.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowDefinitionValidator.Violation;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowInstance.Phase;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.domain.WorkflowTask.Decision;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.adapter.PersistenceNotificationPort;
import com.bone.workflow.starter.adapter.RecordFieldApproverResolver;
import com.bone.workflow.starter.sdk.WfNotificationSdkRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.bone.workflow.starter.spi.InMemoryOrgHierarchyProvider;
import com.bone.workflow.starter.spi.OrgHierarchyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 迁移 0083（报销 / 采购流程 v3）的落地验证：<b>直接加载迁移脚本里的定义 JSON</b>，用真引擎跑一遍。
 *
 * <p><b>为什么不另写一份定义对象</b>：那样验证的是「我心里想的那张图」，而不是「真正会写进 {@code wf_process_def} 的那张图」。
 * 两者的差异正是「配了不生效」的温床——脚本里少写一个 {@code assignments}、JSON 少一个逗号、策略名拼错，单元测试照样全绿，而线上单据挂起。 故本测试从 {@code
 * scripts/migration/0083_*.sql} 中抽取 JSON 字面量原样反序列化，脚本即被测对象。
 *
 * <p><b>判别力的来源（负向探针）</b>：单据数据里刻意塞入 {@code approver_id=decoy-9} 这个「诱饵审批人」。 v3 若仍是 {@code
 * FIELD/approver_id} 策略（即切换没生效），待办会派给 {@code decoy-9}，断言立刻红； 只有真正走组织层级（{@code LINE_MANAGER}/{@code
 * DEPT_HEAD}）才会派给组织目录里的人。 只断言「派给了某人」是不够的——那无法区分「新策略生效」与「旧策略恰好也解析出了人」。
 */
class OrgRoutingDefinitionV3Test {

  /** 迁移脚本相对本模块的相对路径（Surefire 以模块 basedir 为工作目录）。 */
  private static final Path MIGRATION_SQL =
      Paths.get("../../../scripts/migration/0083_expense_org_routing_v3.sql");

  private static final long TENANT = 0L;
  private static final String INITIATOR = "u1";

  /** 组织目录：u1 的主管 u-mgr 与 u1 的部门负责人 u-head 是两个不同的人（这正是 v1/v2 做不到的）。 */
  private static final OrgHierarchyProvider ORG =
      new InMemoryOrgHierarchyProvider()
          .withLineManager(INITIATOR, "u-mgr")
          .withDeptHead(INITIATOR, "u-head");

  /**
   * 诱饵：表单字段 {@code approver_id} 的值。
   *
   * <p>v1/v2 用它当审批人（申请人自选）；v3 起不再参与路由。把它设成一个绝不该收到待办的人， 就能用「待办派给了谁」反证策略确实换了。
   */
  private static final String DECOY_APPROVER = "decoy-9";

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private ObjectMapper mapper;
  private PersistenceNotificationPort notificationPort;
  private final AtomicInteger idSeq = new AtomicInteger();

  /** 记录 id → 单据数据（含金额与诱饵审批人）。 */
  private final Map<String, Map<String, Object>> records = new HashMap<>();

  @BeforeEach
  void setUp() throws Exception {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    mapper = context.getBean(ObjectMapper.class);
    notificationPort =
        new PersistenceNotificationPort(
            context.getBean(WfNotificationSdkRepository.class),
            () -> "N" + idSeq.incrementAndGet());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  // ====================== 定义本身 ======================

  @Test
  @DisplayName("迁移 0083 的两份 v3 定义都能被发布期校验接受（不是「能存进去」而是「能发布」）")
  void migrationDefinitionsArePublishable() throws Exception {
    WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();
    for (String key : List.of("expense_reimbursement", "purchase_order")) {
      WorkflowDefinition def = migrationDefinition(key);
      assertEquals(3, def.version(), key + " 的 v3 定义版本号必须是 3");
      List<Violation> violations = validator.validate(def);
      assertTrue(violations.isEmpty(), key + " 的 v3 定义未通过发布期校验：" + describe(violations));
    }
  }

  // ====================== 报销：三个金额档位各走对人 ======================

  @Test
  @DisplayName("报销-小额(≤1000)：待办派给组织目录里的直属主管，而不是表单上的「指定审批人」")
  void expenseSmall_routesToLineManagerNotFormField() throws Exception {
    seedMigrationDefinitions();
    records.put("r-small", record(800));
    WorkflowEngine engine = engine(ORG);

    WorkflowInstance instance =
        engine.start("expense_reimbursement", "r-small", "expense_reimbursement", INITIATOR);

    assertEquals("mgr", instance.currentNodeCode(), "800 元应停在直属主管节点");
    List<WorkflowTask> pending = engine.listPendingTasks(instance.id());
    assertEquals(1, pending.size());
    assertEquals(
        "u-mgr",
        pending.get(0).assigneeId(),
        "应派给组织目录中的直属主管；派给 " + DECOY_APPROVER + " 说明 v3 的 assignments 还是 FIELD/approver_id（切换未生效）");
    assertNotEquals(DECOY_APPROVER, pending.get(0).assigneeId(), "审批人不得来自申请人自选的表单字段");
  }

  @Test
  @DisplayName("报销-中额(>1000)：待办派给部门负责人，且与主管不是同一个人（分级语义恢复）")
  void expenseMid_routesToDeptHeadDistinctFromLineManager() throws Exception {
    seedMigrationDefinitions();
    records.put("r-mid", record(2000));
    WorkflowEngine engine = engine(ORG);

    WorkflowInstance instance =
        engine.start("expense_reimbursement", "r-mid", "expense_reimbursement", INITIATOR);

    assertEquals("dept", instance.currentNodeCode(), "2000 元应停在部门负责人节点");
    String assignee = engine.listPendingTasks(instance.id()).get(0).assigneeId();
    assertEquals("u-head", assignee, "应派给组织目录中的部门负责人");
    assertNotEquals("u-mgr", assignee, "部门负责人不得等于直属主管——那正是 v1/v2 共用 approver_id 造成的降级形态");
  }

  @Test
  @DisplayName("报销-大额(>5000)：会签节点仍按审批矩阵派两人，不受组织改造影响")
  void expenseLarge_keepsCountersignMatrix() throws Exception {
    seedMigrationDefinitions();
    records.put("r-large", record(8000));
    WorkflowEngine engine = engine(ORG);

    WorkflowInstance instance =
        engine.start("expense_reimbursement", "r-large", "expense_reimbursement", INITIATOR);

    assertEquals("finance_cosign", instance.currentNodeCode());
    List<String> assignees =
        engine.listPendingTasks(instance.id()).stream()
            .map(WorkflowTask::assigneeId)
            .sorted()
            .toList();
    assertEquals(List.of("9104", "9105"), assignees, "大额会签是审批矩阵（USER 策略），不随组织改造漂移");
  }

  @Test
  @DisplayName("报销-组织数据缺失（无主管）：v3 失败关闭挂起，不放行、不退回表单字段")
  void expenseWithoutManager_suspendsFailClosed() throws Exception {
    seedMigrationDefinitions();
    records.put("r-nomgr", record(800));
    // 空的组织目录：既没有主管，也没有部门负责人
    WorkflowEngine engine = engine(new InMemoryOrgHierarchyProvider());

    WorkflowInstance instance =
        engine.start("expense_reimbursement", "r-nomgr", "expense_reimbursement", INITIATOR);

    assertEquals(
        Phase.SUSPENDED, instance.phase(), "解析不出审批人时必须挂起。若这里是 RUNNING/APPROVED，说明 v3 悄悄退化成了别的策略");
    assertTrue(engine.listPendingTasks(instance.id()).isEmpty(), "挂起节点不得生成幽灵待办");
  }

  // ====================== 采购：单级审批同样收敛到部门负责人 ======================

  @Test
  @DisplayName("采购-常规单：待办派给部门负责人，而不是表单上的「指定审批人」")
  void purchaseStandard_routesToDeptHead() throws Exception {
    seedMigrationDefinitions();
    records.put("p-small", record(5000));
    WorkflowEngine engine = engine(ORG);

    WorkflowInstance instance =
        engine.start("purchase_order", "p-small", "purchase_order", INITIATOR);

    assertEquals("approve", instance.currentNodeCode());
    String assignee = engine.listPendingTasks(instance.id()).get(0).assigneeId();
    assertEquals("u-head", assignee, "采购单级审批应派给部门负责人");
    assertNotEquals(DECOY_APPROVER, assignee, "审批人不得来自申请人自选的表单字段");
  }

  // ====================== 工具 ======================

  /**
   * 从迁移脚本中抽取指定 key 的 v3 定义 JSON 并反序列化。
   *
   * <p>脚本里每条定义的 JSON 独占一行、以单引号包裹，故按行取「首尾引号之间」即可；找不到时直接失败并提示， 绝不静默跳过（跳过 = 这个测试文件在 CI 里变成永远绿的空壳）。
   */
  private WorkflowDefinition migrationDefinition(String defKey) throws IOException {
    assertTrue(
        Files.exists(MIGRATION_SQL),
        "找不到迁移脚本 "
            + MIGRATION_SQL.toAbsolutePath().normalize()
            + "（Surefire 以模块目录为工作目录；若模块布局变更需同步本路径）");
    String needle = "\"key\":\"" + defKey + "\"";
    String json =
        Files.readAllLines(MIGRATION_SQL, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> line.startsWith("'{\"key\":\""))
            .map(line -> line.substring(1, line.lastIndexOf('\'')))
            .filter(candidate -> candidate.contains(needle))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("迁移脚本里没有 " + defKey + " 的定义 JSON 字面量（预期独占一行、单引号包裹）"));
    return mapper.readValue(json, WorkflowDefinition.class);
  }

  /** 把迁移脚本里的 v3 定义写进 H2 的 {@code wf_process_def}（版本 3 = 该 key 的最高发布版）。 */
  private void seedMigrationDefinitions() throws Exception {
    for (String key : List.of("expense_reimbursement", "purchase_order")) {
      WorkflowDefinition def = migrationDefinition(key);
      jdbc.update(
          "INSERT INTO wf_process_def (id, tenant_id, def_key, def_name, def_version, status,"
              + " enabled, definition_json, created_by, created_at, updated_by, updated_at,"
              + " deleted, version)"
              + " VALUES (?, ?, ?, ?, ?, 'PUBLISHED', 1, ?, 't', NOW(), 't', NOW(), 0, 0)",
          "D-" + key + "-v3",
          TENANT,
          def.key(),
          def.name(),
          def.version(),
          mapper.writeValueAsString(def));
    }
  }

  /** 单据数据：金额决定档位，同时塞入诱饵审批人。 */
  private Map<String, Object> record(int amount) {
    return Map.of("amount", amount, "approver_id", DECOY_APPROVER);
  }

  private WorkflowEngine engine(OrgHierarchyProvider orgProvider) {
    WorkflowRecordPort recordPort =
        (entityCode, recordId) -> records.getOrDefault(recordId, Map.of());
    return new WorkflowEngineImpl(
        context.getBean(WorkflowDefinitionRepository.class),
        context.getBean(WorkflowInstanceRepository.class),
        new RecordFieldApproverResolver(null, orgProvider),
        recordPort,
        noopStatusPort(),
        noopAudit(),
        () -> "W" + idSeq.incrementAndGet(),
        new StructuredConditionEvaluator(),
        new WorkflowDefinitionValidator(),
        Clock.systemUTC(),
        notificationPort);
  }

  private String describe(List<Violation> violations) {
    return violations.stream()
        .map(v -> v.code() + "@" + v.locator() + " " + v.message())
        .collect(Collectors.joining("; "));
  }

  private WorkflowStatusPort noopStatusPort() {
    return new WorkflowStatusPort() {
      @Override
      public void markApproved(
          String entityCode, String recordId, String instanceId, String operatorId) {}

      @Override
      public void markRejected(
          String entityCode,
          String recordId,
          String instanceId,
          String operatorId,
          String reason) {}

      @Override
      public void markWithdrawn(
          String entityCode, String recordId, String instanceId, String operatorId) {}

      @Override
      public void markSuspended(
          String entityCode, String recordId, String instanceId, String reason) {}

      @Override
      public void markTerminated(
          String entityCode,
          String recordId,
          String instanceId,
          String operatorId,
          String reason) {}

      @Override
      public void markResumed(String entityCode, String recordId, String instanceId) {}
    };
  }

  private WorkflowAuditPort noopAudit() {
    return new WorkflowAuditPort() {
      @Override
      public void recordStart(WorkflowInstance instance, String operatorId) {}

      @Override
      public void recordAdvance(
          WorkflowInstance instance, String fromNode, String toNode, String branchLabel) {}

      @Override
      public void recordDecision(WorkflowTask task, Decision decision, String comment) {}

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
}
