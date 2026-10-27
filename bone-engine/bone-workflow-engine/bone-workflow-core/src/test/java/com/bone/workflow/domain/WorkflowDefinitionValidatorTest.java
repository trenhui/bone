package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 流程定义发布期校验器的判别力测试。
 *
 * <p>核心命题：<b>配错的流程不许发布</b>。每种错误配置都对应一个必红用例——把任一检查删掉，本类应变红。
 */
class WorkflowDefinitionValidatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

  private static com.fasterxml.jackson.databind.JsonNode json(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static WorkflowDefinition.Node start() {
    return new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起");
  }

  private static WorkflowDefinition.Node approve(String code) {
    return new WorkflowDefinition.Node(
        code, WorkflowDefinition.NodeType.APPROVE, WorkflowDefinition.ApprovalMode.SINGLE, code);
  }

  private static WorkflowDefinition.Node end() {
    return new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束");
  }

  private static WorkflowDefinition.Edge edge(String from, String to, String when) {
    return new WorkflowDefinition.Edge(from, to, when == null ? null : json(when), from + "→" + to);
  }

  private static WorkflowDefinition.AssignmentRule rule(String strategy) {
    return new WorkflowDefinition.AssignmentRule(strategy, Map.of());
  }

  /** 一条合法的线性流程：start → approve → end。 */
  private static WorkflowDefinition linear() {
    return new WorkflowDefinition(
        "expense",
        1,
        List.of(start(), approve("mgr"), end()),
        List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
        Map.of("mgr", List.of(rule("LINE_MANAGER"))));
  }

  private static boolean hasCode(
      List<WorkflowDefinitionValidator.Violation> violations, String code) {
    return violations.stream().anyMatch(violation -> violation.code().equals(code));
  }

  @Test
  @DisplayName("合法线性流程 → 无违规")
  void validDefinition_hasNoViolation() {
    assertTrue(validator.validate(linear()).isEmpty());
    assertTrue(validator.isPublishable(linear()));
  }

  @Test
  @DisplayName("缺发起节点 → WF-002")
  void missingStart_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(approve("mgr"), end()),
            List.of(edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-002"));
  }

  @Test
  @DisplayName("缺结束节点 → WF-003")
  void missingEnd_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr")),
            List.of(edge("start", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-003"));
  }

  @Test
  @DisplayName("结束节点有后继 → WF-004")
  void endWithOutEdge_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null), edge("end", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-004"));
  }

  @Test
  @DisplayName("中间节点断头 → WF-005")
  void danglingNode_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-005"));
  }

  @Test
  @DisplayName("审批节点未配指派规则 → WF-007")
  void approveWithoutAssignment_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of());
    assertTrue(hasCode(validator.validate(definition), "WF-007"));
  }

  @Test
  @DisplayName("未知指派策略 → WF-013")
  void unknownAssignmentStrategy_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("WIZARD"))));
    assertTrue(hasCode(validator.validate(definition), "WF-013"));
  }

  @Test
  @DisplayName("条件节点：出边不足 / 缺默认边 / 默认边不在末位 / 条件非法 → WF-006")
  void conditionNode_problems_areRejected() {
    WorkflowDefinition.Node condition =
        new WorkflowDefinition.Node("cond", WorkflowDefinition.NodeType.CONDITION, null, "分支");

    WorkflowDefinition onlyOneEdge =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), end()),
            List.of(
                edge("start", "cond", null),
                edge(
                    "cond",
                    "mgr",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(onlyOneEdge), "WF-006"));

    WorkflowDefinition noDefault =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), approve("dir"), end()),
            List.of(
                edge("start", "cond", null),
                edge(
                    "cond", "mgr", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
                edge(
                    "cond",
                    "dir",
                    "{\"fieldCode\":\"amount\",\"operator\":\"lt\",\"value\":1000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "dir", List.of(rule("DEPT_HEAD"))));
    assertTrue(hasCode(validator.validate(noDefault), "WF-006"));

    WorkflowDefinition defaultNotLast =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), approve("dir"), end()),
            List.of(
                edge("start", "cond", null),
                edge("cond", "mgr", null),
                edge(
                    "cond",
                    "dir",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "dir", List.of(rule("DEPT_HEAD"))));
    assertTrue(hasCode(validator.validate(defaultNotLast), "WF-006"));

    WorkflowDefinition badCondition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), condition, approve("mgr"), end()),
            List.of(
                edge("start", "cond", null),
                edge("cond", "mgr", "{\"fieldCode\":\"amount\",\"operator\":\"hack\",\"value\":1}"),
                edge("cond", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(badCondition), "WF-006"));
  }

  @Test
  @DisplayName("边指向不存在的节点 → WF-008")
  void edgeToUnknownNode_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "ghost", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-008"));
  }

  @Test
  @DisplayName("孤岛节点（从发起节点不可达）→ WF-009")
  void orphanNode_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), approve("orphan"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "orphan", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-009"));
  }

  @Test
  @DisplayName("流程成环 → WF-010（v1 不支持回环审批）")
  void cyclicFlow_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("a"), approve("b"), end()),
            List.of(
                edge("start", "a", null),
                edge("a", "b", null),
                edge("b", "a", null),
                edge("b", "end", null)),
            Map.of("a", List.of(rule("ROLE")), "b", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-010"));
  }

  @Test
  @DisplayName("非审批节点带完成判定方式 → WF-012")
  void nonApproveWithMode_isRejected() {
    WorkflowDefinition.Node badStart =
        new WorkflowDefinition.Node(
            "start", WorkflowDefinition.NodeType.START, WorkflowDefinition.ApprovalMode.ALL, "发起");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(badStart, approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-012"));
  }

  @Test
  @DisplayName("审批节点多条出边（把条件写在审批节点上）→ WF-014")
  void approveNodeWithMultipleOutEdges_isRejected() {
    // 历史文档示例的错误形状：edge("mgr","finance","amount > 5000") + edge("mgr","end")
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), approve("finance"), end()),
            List.of(
                edge("start", "mgr", null),
                edge(
                    "mgr",
                    "finance",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
                edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "finance", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-014"));
  }

  @Test
  @DisplayName("审批节点唯一出边带条件 → WF-014（运行期会静默忽略条件，必须发布期拦下）")
  void approveNodeWithConditionalEdge_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(
                edge("start", "mgr", null),
                edge(
                    "mgr", "end", "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}")),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-014"));
  }

  @Test
  @DisplayName("发起节点多条出边 / 出边带条件 → WF-015")
  void startNodeWithMultipleOrConditionalOutEdges_isRejected() {
    WorkflowDefinition multiple =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(edge("start", "mgr", null), edge("start", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(multiple), "WF-015"));

    WorkflowDefinition conditional =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr"), end()),
            List.of(
                edge(
                    "start",
                    "mgr",
                    "{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
                edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(conditional), "WF-015"));
  }

  @Test
  @DisplayName("抄送节点（CC）缺抄送人指派规则 → WF-018（抄送已落地，不再一律拒绝）")
  void ccNode_withoutAssignees_isRejected() {
    WorkflowDefinition.Node cc =
        new WorkflowDefinition.Node("cc", WorkflowDefinition.NodeType.CC, null, "抄送");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), cc, approve("mgr"), end()),
            List.of(edge("start", "cc", null), edge("cc", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-018"));
  }

  @Test
  @DisplayName("抄送节点（CC）配了抄送人 → 通过校验（不再被 WF-016 一律拒绝）")
  void ccNode_withAssignees_isAccepted() {
    WorkflowDefinition.Node cc =
        new WorkflowDefinition.Node("cc", WorkflowDefinition.NodeType.CC, null, "抄送");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), cc, approve("mgr"), end()),
            List.of(edge("start", "cc", null), edge("cc", "mgr", null), edge("mgr", "end", null)),
            Map.of(
                "mgr",
                List.of(rule("LINE_MANAGER")),
                // 原夹具用 rule("ROLE")（空参数）也算「配了抄送人」——WF-020 之后不再成立：
                // 没有 roleCode 的 ROLE 在运行期解析不出收件人，抄送实际不会发生。
                "cc",
                List.of(
                    new WorkflowDefinition.AssignmentRule("ROLE", Map.of("roleCode", "audit")))));
    List<WorkflowDefinitionValidator.Violation> violations = validator.validate(definition);
    assertFalse(hasCode(violations, "WF-016"), "抄送已落地，不应再被 WF-016 拒绝");
    assertFalse(hasCode(violations, "WF-018"), "配了抄送人指派规则即应通过");
    assertTrue(validator.isPublishable(definition));
  }

  @Test
  @DisplayName("抄送节点 ROLE 缺 roleCode → WF-020（抄送会静默不发出）")
  void ccNode_roleWithoutRoleCode_isRejected() {
    WorkflowDefinition.Node cc =
        new WorkflowDefinition.Node("cc", WorkflowDefinition.NodeType.CC, null, "抄送");
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), cc, approve("mgr"), end()),
            List.of(edge("start", "cc", null), edge("cc", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER")), "cc", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-020"));
  }

  @Test
  @DisplayName("合法线性流程不受新规则影响（防过度拦截）")
  void validDefinition_stillPassesWithNewRules() {
    assertTrue(validator.validate(linear()).isEmpty());
  }

  @Test
  @DisplayName("默认格式版本（当前版本）不触发 WF-017")
  void currentSchemaVersion_isAccepted() {
    assertTrue(validator.validate(linear()).isEmpty());
    assertTrue(validator.isPublishable(linear()));
  }

  @Test
  @DisplayName("格式版本高于引擎上限 → WF-017（引擎太旧，fail-loud）")
  void schemaVersionAboveEngineLimit_isRejected() {
    int future = WorkflowDefinition.CURRENT_ENGINE_SCHEMA_VERSION + 1;
    WorkflowDefinition definition = withSchema(future);
    assertTrue(hasCode(validator.validate(definition), "WF-017"));
    assertTrue(!validator.isPublishable(definition));
  }

  @Test
  @DisplayName("格式版本低于引擎下限 → WF-017（格式已废弃）")
  void schemaVersionBelowMinimum_isRejected() {
    WorkflowDefinition definition =
        withSchema(WorkflowDefinition.MIN_SUPPORTED_ENGINE_SCHEMA_VERSION - 1);
    assertTrue(hasCode(validator.validate(definition), "WF-017"));
  }

  /** 带显式格式版本的合法线性流程。 */
  private static WorkflowDefinition withSchema(int schema) {
    return new WorkflowDefinition(
        "expense",
        "报销",
        1,
        schema,
        List.of(start(), approve("mgr"), end()),
        List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
        Map.of("mgr", List.of(rule("LINE_MANAGER"))));
  }

  private static WorkflowDefinition.Node approveRatio(String code, int quorum) {
    return new WorkflowDefinition.Node(
        code,
        WorkflowDefinition.NodeType.APPROVE,
        WorkflowDefinition.ApprovalMode.RATIO,
        code,
        quorum);
  }

  @Test
  @DisplayName("比例会签(RATIO)未配法定人数 quorum → WF-019（不许发布）")
  void ratioWithoutQuorum_isRejected() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approveRatio("committee", 0), end()),
            List.of(edge("start", "committee", null), edge("committee", "end", null)),
            Map.of("committee", List.of(rule("ROLE"))));
    assertTrue(hasCode(validator.validate(definition), "WF-019"));
    assertTrue(!validator.isPublishable(definition));
  }

  @Test
  @DisplayName("合法比例会签(RATIO)配 quorum≥1 → 无违规（可发布）")
  void validRatio_isPublishable() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approveRatio("committee", 2), end()),
            List.of(edge("start", "committee", null), edge("committee", "end", null)),
            // 此前该夹具用 rule("ROLE")（空参数）也能通过——正是 WF-020 要堵的洞：
            // 运行期 resolveByRole 读到 null 的 roleCode 会返空列表，节点挂起。故补上 roleCode。
            Map.of(
                "committee",
                List.of(
                    new WorkflowDefinition.AssignmentRule(
                        "ROLE", Map.of("roleCode", "committee")))));
    assertTrue(validator.validate(definition).isEmpty());
    assertTrue(validator.isPublishable(definition));
  }

  @Test
  @DisplayName("非比例会签节点设置 quorum → WF-019（quorum 仅对 RATIO 有效）")
  void quorumOnNonRatioNode_isRejected() {
    // 复用 4 参便捷构造得到 quorum=null 的 SINGLE 节点；此处反过来：SINGLE 却带 quorum 必须由校验器拦下。
    // 直接以 5 参构造一个「SINGLE + quorum=2」的非法节点。
    WorkflowDefinition.Node bad =
        new WorkflowDefinition.Node(
            "mgr",
            WorkflowDefinition.NodeType.APPROVE,
            WorkflowDefinition.ApprovalMode.SINGLE,
            "主管",
            2);
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), bad, end()),
            List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    assertTrue(hasCode(validator.validate(definition), "WF-019"));
  }

  // ====================== WF-020：策略缺必需参数 ======================

  private static WorkflowDefinition withRule(WorkflowDefinition.AssignmentRule rule) {
    return new WorkflowDefinition(
        "expense",
        1,
        List.of(start(), approve("mgr"), end()),
        List.of(edge("start", "mgr", null), edge("mgr", "end", null)),
        Map.of("mgr", List.of(rule)));
  }

  @Test
  @DisplayName("ROLE 策略缺 roleCode → WF-020（否则发布成功、运行期挂起）")
  void roleWithoutRoleCode_isRejected() {
    WorkflowDefinition definition =
        withRule(new WorkflowDefinition.AssignmentRule("ROLE", Map.of()));
    List<WorkflowDefinitionValidator.Violation> violations = validator.validate(definition);
    assertTrue(hasCode(violations, "WF-020"));
    assertFalse(validator.isPublishable(definition));
  }

  @Test
  @DisplayName("USER 策略写成 userIds（复数）→ WF-020：实现读的是单数 userId")
  void userWithPluralKey_isRejected() {
    WorkflowDefinition definition =
        withRule(new WorkflowDefinition.AssignmentRule("USER", Map.of("userIds", List.of("u1"))));
    assertTrue(hasCode(validator.validate(definition), "WF-020"));
  }

  @Test
  @DisplayName("FIELD 策略缺 fieldCode → WF-020；补齐后放行")
  void fieldStrategy_requiresFieldCode() {
    assertTrue(
        hasCode(
            validator.validate(withRule(new WorkflowDefinition.AssignmentRule("FIELD", Map.of()))),
            "WF-020"));
    assertTrue(
        validator
            .validate(
                withRule(
                    new WorkflowDefinition.AssignmentRule("FIELD", Map.of("fieldCode", "mgr"))))
            .isEmpty());
  }

  @Test
  @DisplayName("LEVEL_UP 的 level 非正数 → WF-020")
  void levelUpWithNonPositiveLevel_isRejected() {
    assertTrue(
        hasCode(
            validator.validate(
                withRule(new WorkflowDefinition.AssignmentRule("LEVEL_UP", Map.of("level", 0)))),
            "WF-020"));
  }

  @Test
  @DisplayName("无参策略（LINE_MANAGER / DEPT_HEAD）不因 WF-020 误伤")
  void parameterlessStrategies_areNotFlagged() {
    assertTrue(
        validator
            .validate(withRule(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of())))
            .isEmpty());
    assertTrue(
        validator
            .validate(withRule(new WorkflowDefinition.AssignmentRule("DEPT_HEAD", null)))
            .isEmpty());
  }

  @Test
  @DisplayName("合规节点级违规带 locator，前端可据此高亮画布节点")
  void nodeScopedViolation_carriesLocator() {
    WorkflowDefinition definition =
        new WorkflowDefinition(
            "expense",
            1,
            List.of(start(), approve("mgr")),
            List.of(edge("start", "mgr", null)),
            Map.of("mgr", List.of(rule("LINE_MANAGER"))));
    // 缺 END → WF-003 是整图级问题，不应有 locator
    assertTrue(
        validator.validate(definition).stream()
            .filter(violation -> "WF-003".equals(violation.code()))
            .allMatch(violation -> !violation.isNodeScoped()));
    // 断头 → WF-005 必须定位到 mgr
    assertTrue(
        validator.validate(definition).stream()
            .filter(violation -> "WF-005".equals(violation.code()))
            .anyMatch(violation -> "mgr".equals(violation.locator())));
  }
}
