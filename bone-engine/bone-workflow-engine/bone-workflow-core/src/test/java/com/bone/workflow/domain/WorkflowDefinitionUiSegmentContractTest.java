package com.bone.workflow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 定义 JSON 的<b>扩展段容忍性</b>契约测试（设计器 M0）。
 *
 * <p>为什么必须是契约测试而不是「跑一遍看有没有炸」：定义 JSON 除引擎字段外还承载设计器专用段 {@code ui} （画布坐标、折叠状态，先例为 BPMN 的 {@code
 * BPMNDiagram}）。引擎若不容忍它，故障形态极其隐蔽—— 定义能发布、能落库，直到某个实例加载时才炸，而错误信息是 {@code Unrecognized field
 * "ui"}，指向的是 「发起/推进」而不是「建模」。
 *
 * <p>这里刻意用<b>严格模式的裸 {@link ObjectMapper}</b>（不是 Spring 容器里那份宽容的）：如果把容忍性放在 装配层的 mapper 配置上，任何一处
 * {@code new ObjectMapper()} 都会让它失效——2026-10-27 就在 starter 测试上下文中真实复现过。容忍性写在 {@link
 * WorkflowDefinition} 类型上，才与「谁构造 mapper」无关。
 */
class WorkflowDefinitionUiSegmentContractTest {

  private static final ObjectMapper STRICT = new ObjectMapper();

  /** 带设计器 ui 段的定义（ui 内含布局与文档树原文）。 */
  private static final String WITH_UI =
      """
      {"key":"expense","name":"费用报销","version":3,"engineSchemaVersion":1,
       "nodes":[{"code":"start","type":"START","label":"发起"},
                {"code":"mgr","type":"APPROVE","mode":"SINGLE","label":"主管","quorum":null},
                {"code":"end","type":"END","label":"结束"}],
       "edges":[{"from":"start","to":"mgr","when":null,"label":null},
                {"from":"mgr","to":"end"}],
       "assignments":{"mgr":[{"strategy":"ROLE","params":{"roleCode":"fin"}}]},
       "ui":{"layout":{"start":{"x":0,"y":0},"mgr":{"x":0,"y":120}},
             "flowDoc":{"key":"expense","name":"费用报销","steps":[]}}}
      """;

  @Test
  @DisplayName("严格 mapper 下带 ui 段的定义可正常解析：已知字段全对，ui 被忽略")
  void uiSegment_isToleratedByStrictMapper() throws Exception {
    WorkflowDefinition definition = STRICT.readValue(WITH_UI, WorkflowDefinition.class);

    assertEquals("expense", definition.key());
    assertEquals("费用报销", definition.name());
    assertEquals(3, definition.version());
    assertEquals(1, definition.engineSchemaVersion());
    assertEquals(3, definition.nodes().size());
    assertEquals(2, definition.edges().size());
    assertEquals("fin", definition.assignments().get("mgr").get(0).params().get("roleCode"));
  }

  @Test
  @DisplayName("节点顺序与默认边位置不被 ui 段影响（边的语义顺序即出边顺序）")
  void edgeOrder_survivesUnknownFields() throws Exception {
    WorkflowDefinition definition = STRICT.readValue(WITH_UI, WorkflowDefinition.class);

    List<WorkflowDefinition.Edge> outs = definition.outEdges("mgr");
    assertEquals(1, outs.size());
    assertTrue(outs.get(0).isDefault(), "when 缺省应判为默认边");
  }

  @Test
  @DisplayName("向后兼容：老定义没有 engineSchemaVersion 时按当前格式版本解释")
  void missingSchemaVersion_defaultsToCurrent() throws Exception {
    String legacy =
        """
        {"key":"expense","name":"费用报销","version":1,"nodes":[],"edges":[],"assignments":{}}
        """;
    WorkflowDefinition definition = STRICT.readValue(legacy, WorkflowDefinition.class);

    assertNotNull(definition.engineSchemaVersion());
    assertEquals(
        WorkflowDefinition.CURRENT_ENGINE_SCHEMA_VERSION, definition.engineSchemaVersion());
  }

  @Test
  @DisplayName("发布期校验不因 ui 段报违规（定义本身合法则零违规）")
  void validation_isUnaffectedByUiSegment() throws Exception {
    WorkflowDefinition definition = STRICT.readValue(WITH_UI, WorkflowDefinition.class);
    assertTrue(new WorkflowDefinitionValidator().validate(definition).isEmpty());
  }

  @Test
  @DisplayName("未知的引擎字段同样被忽略：结构演进不会让存量定义读不回来")
  void unknownEngineFields_areIgnored() throws Exception {
    String withFutureField =
        WITH_UI.replace(
            "\"engineSchemaVersion\":1", "\"engineSchemaVersion\":1,\"futureFlag\":true");
    WorkflowDefinition definition = STRICT.readValue(withFutureField, WorkflowDefinition.class);
    assertEquals("expense", definition.key());
  }
}
