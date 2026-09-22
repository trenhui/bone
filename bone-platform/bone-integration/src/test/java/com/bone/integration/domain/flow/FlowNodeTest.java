package com.bone.integration.domain.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link FlowNode} 纯单测：节点创建约束与编排更新语义（无容器）。 */
class FlowNodeTest {

  @Test
  void testCreateRecordsNodeProfile() {
    FlowNode node =
        FlowNode.create(1L, 100L, "调用订单接口", NodeType.HTTP, Map.of("url", "http://order"), 10, 20);

    assertEquals(100L, node.getFlowId());
    assertEquals("调用订单接口", node.getName());
    assertEquals(NodeType.HTTP, node.getType());
    assertEquals(10, node.getPositionX());
    assertEquals(20, node.getPositionY());
  }

  @Test
  void testCreateRejectsInvalidArguments() {
    assertThrows(
        DomainException.class,
        () -> FlowNode.create(1L, null, "节点", NodeType.HTTP, Map.of("a", 1), 0, 0));
    assertThrows(
        DomainException.class,
        () -> FlowNode.create(1L, 100L, " ", NodeType.HTTP, Map.of("a", 1), 0, 0));
    assertThrows(
        DomainException.class, () -> FlowNode.create(1L, 100L, "节点", null, Map.of("a", 1), 0, 0));
    assertThrows(
        DomainException.class, () -> FlowNode.create(1L, 100L, "节点", NodeType.HTTP, null, 0, 0));
  }

  @Test
  void testUpdateRepositionsNodeAndRejectsNullConfig() {
    FlowNode node =
        FlowNode.create(1L, 100L, "调用订单接口", NodeType.HTTP, Map.of("url", "http://order"), 10, 20);

    node.update("数据转换", Map.of("script", "a+b"), 30, 40);

    assertEquals("数据转换", node.getName());
    // update 不改变节点类型
    assertEquals(NodeType.HTTP, node.getType());
    assertEquals(30, node.getPositionX());
    assertEquals(40, node.getPositionY());

    assertThrows(DomainException.class, () -> node.update(" ", Map.of("x", 1), 0, 0));
    assertThrows(DomainException.class, () -> node.update("数据转换", null, 0, 0));
    // 校验失败时原名称保持不变
    assertEquals("数据转换", node.getName());
  }
}
