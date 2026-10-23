package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 流程定义 JSON 往返（序列化 → 落库 → 读回）的判别力测试。
 *
 * <p>核心命题：<b>经 {@code definition_json} 落库的定义必须能原样读回</b>。这是「配了不生效」的镜像事故——发布成功、 start
 * 必崩，且内存构造的测试永远绿。{@link WorkflowDefinition.Edge#isDefault()} 是派生判定（由 {@code when} 推导）， 曾被 Jackson 按
 * getter 约定序列化成 {@code "default"} 字段，读回时 {@code UnrecognizedPropertyException}—— 删掉
 * {@code @JsonIgnore} 本类应立即变红。
 */
class WorkflowDefinitionJsonRoundTripTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("定义序列化后能原样读回（含条件边与默认边）")
  void definitionSurvivesJsonRoundTrip() throws Exception {
    WorkflowDefinition def = definition();

    String stored = MAPPER.writeValueAsString(def);
    WorkflowDefinition readBack = MAPPER.readValue(stored, WorkflowDefinition.class);

    assertEquals(def.key(), readBack.key());
    assertEquals(def.nodes(), readBack.nodes());
    assertEquals(def.edges(), readBack.edges());
    assertEquals(def.assignments(), readBack.assignments());
  }

  @Test
  @DisplayName("往返后条件边 / 默认边的判定不漂移")
  void edgeDefaultnessSurvivesRoundTrip() throws Exception {
    WorkflowDefinition def = definition();

    String stored = MAPPER.writeValueAsString(def);
    WorkflowDefinition readBack = MAPPER.readValue(stored, WorkflowDefinition.class);

    WorkflowDefinition.Edge conditional = def.outEdges("cond").get(0);
    WorkflowDefinition.Edge fallback = def.outEdges("cond").get(1);
    assertFalse(conditional.isDefault(), "前置条件：条件边不得判为默认边");
    assertTrue(fallback.isDefault(), "前置条件：when 为空的边必须判为默认边");

    WorkflowDefinition.Edge readConditional = readBack.outEdges("cond").get(0);
    WorkflowDefinition.Edge readFallback = readBack.outEdges("cond").get(1);
    assertFalse(readConditional.isDefault(), "读回后条件边判定漂移 ⇒ 分支选择会走错边");
    assertTrue(readFallback.isDefault(), "读回后默认边判定漂移 ⇒ 条件不命中时无路可走");
  }

  private static JsonNode tree(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static WorkflowDefinition definition() {
    WorkflowDefinition.Node start =
        new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起");
    WorkflowDefinition.Node cond =
        new WorkflowDefinition.Node("cond", WorkflowDefinition.NodeType.CONDITION, null, "金额分流");
    WorkflowDefinition.Node high =
        new WorkflowDefinition.Node(
            "approve_high",
            WorkflowDefinition.NodeType.APPROVE,
            WorkflowDefinition.ApprovalMode.SINGLE,
            "总监批");
    WorkflowDefinition.Node low =
        new WorkflowDefinition.Node(
            "approve_low",
            WorkflowDefinition.NodeType.APPROVE,
            WorkflowDefinition.ApprovalMode.SINGLE,
            "主管批");
    WorkflowDefinition.Node end =
        new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束");

    // 条件边：结构化条件（与平台 UI 协议规则同源）
    JsonNode amountGt5000 =
        tree("{\"all\":[{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}]}");
    WorkflowDefinition.Edge toHigh =
        new WorkflowDefinition.Edge("cond", "approve_high", amountGt5000, "大额");
    // 默认边（else）：when 为空对象
    JsonNode empty = MAPPER.createObjectNode();
    WorkflowDefinition.Edge toLow = new WorkflowDefinition.Edge("cond", "approve_low", empty, "默认");

    Map<String, List<WorkflowDefinition.AssignmentRule>> assignments =
        Map.of(
            "approve_high",
            List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of())),
            "approve_low",
            List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of("roleCode", "FIN_MGR"))));

    return new WorkflowDefinition(
        "expense_reimbursement",
        1,
        List.of(start, cond, high, low, end),
        List.of(
            new WorkflowDefinition.Edge("start", "cond", null, null),
            toHigh,
            toLow,
            new WorkflowDefinition.Edge("approve_high", "end", null, null),
            new WorkflowDefinition.Edge("approve_low", "end", null, null)),
        assignments);
  }
}
