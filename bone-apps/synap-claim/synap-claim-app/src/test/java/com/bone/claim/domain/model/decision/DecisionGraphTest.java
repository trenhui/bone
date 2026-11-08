package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A3 决策图单测（结构校验：主链固定 / 无环 / 节点数上限 / 引用完整）。 */
class DecisionGraphTest {

  @Test
  @DisplayName("标准主链 5 节点，类型映射正确（OCR→RULE→LLM→RULE→DECISION）")
  void canonicalChain() {
    DecisionGraph graph = DecisionGraph.canonical();
    assertThat(graph.nodes()).hasSize(5);
    assertThat(graph.nodes().get(0).name()).isEqualTo(DecisionGraph.EVIDENCE_LOAD);
    assertThat(graph.nodes().get(0).type()).isEqualTo(DecisionNodeType.OCR);
    assertThat(graph.nodes().get(1).type()).isEqualTo(DecisionNodeType.RULE);
    assertThat(graph.nodes().get(2).type()).isEqualTo(DecisionNodeType.LLM);
    assertThat(graph.nodes().get(3).type()).isEqualTo(DecisionNodeType.RULE);
    assertThat(graph.nodes().get(4).type()).isEqualTo(DecisionNodeType.DECISION);
    assertThat(graph.nodes().get(4).next()).isNull();
  }

  @Test
  @DisplayName("环检测：定义期即拒（配置错误早暴露）")
  void cycleRejected() {
    assertThatThrownBy(
            () ->
                DecisionGraph.of(
                    List.of(
                        new DecisionGraph.Node("A", DecisionNodeType.RULE, "B"),
                        new DecisionGraph.Node("B", DecisionNodeType.RULE, "A"))))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("环");
  }

  @Test
  @DisplayName("节点数超限 → 拒")
  void nodeLimit() {
    assertThatThrownBy(
            () ->
                DecisionGraph.of(
                    List.of(
                        new DecisionGraph.Node("A", DecisionNodeType.RULE, "B"),
                        new DecisionGraph.Node("B", DecisionNodeType.RULE, "C"),
                        new DecisionGraph.Node("C", DecisionNodeType.RULE, "D"),
                        new DecisionGraph.Node("D", DecisionNodeType.RULE, "E"),
                        new DecisionGraph.Node("E", DecisionNodeType.RULE, "F"),
                        new DecisionGraph.Node("F", DecisionNodeType.RULE, null))))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("超限");
  }

  @Test
  @DisplayName("后继节点不存在 → 拒")
  void missingSuccessor() {
    assertThatThrownBy(
            () ->
                DecisionGraph.of(
                    List.of(new DecisionGraph.Node("A", DecisionNodeType.RULE, "NOT_EXIST"))))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("后继节点不存在");
  }

  @Test
  @DisplayName("节点名重复 → 拒")
  void duplicatedName() {
    assertThatThrownBy(
            () ->
                DecisionGraph.of(
                    List.of(
                        new DecisionGraph.Node("A", DecisionNodeType.RULE, null),
                        new DecisionGraph.Node("A", DecisionNodeType.LLM, null))))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("重复");
  }
}
