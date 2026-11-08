package com.bone.claim.domain.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.decision.DecisionGraph.Node;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 拓扑单一真源：canonical() 的节点、顺序、类型与可跳过白名单不得各自漂移。 */
class DecisionGraphCanonicalTest {

  @Test
  @DisplayName("canonical 固定 5 节点主链且顺序与 node_type 映射唯一")
  void canonicalTopologyIsSingleSourceOfTruth() {
    List<Node> nodes = DecisionGraph.canonical().nodes();

    assertThat(nodes)
        .extracting(Node::name)
        .containsExactly(
            DecisionGraph.EVIDENCE_LOAD,
            DecisionGraph.RULE_CHECK,
            DecisionGraph.AI_ASSESS,
            DecisionGraph.ADJUST,
            DecisionGraph.ARBITRATE);
    assertThat(nodes)
        .extracting(Node::type)
        .containsExactly(
            DecisionNodeType.OCR,
            DecisionNodeType.RULE,
            DecisionNodeType.LLM,
            DecisionNodeType.RULE,
            DecisionNodeType.DECISION);
    assertThat(nodes)
        .extracting(Node::next)
        .containsExactly(
            DecisionGraph.RULE_CHECK,
            DecisionGraph.AI_ASSESS,
            DecisionGraph.ADJUST,
            DecisionGraph.ARBITRATE,
            null);
    assertThat(nodes).hasSize(DecisionGraph.MAX_NODES);
  }

  @Test
  @DisplayName("对外 node_type 只有 4 类，不新增自定义类型")
  void nodeTypeEnumIsClosed() {
    assertThat(DecisionNodeType.values())
        .containsExactly(
            DecisionNodeType.OCR,
            DecisionNodeType.RULE,
            DecisionNodeType.LLM,
            DecisionNodeType.DECISION);
  }

  @Test
  @DisplayName("唯一可跳过节点是 AiAssess，金额与结论节点绝不外放")
  void skippableWhitelistIsClosed() {
    assertThat(DecisionGraph.SKIPPABLE_NODES).containsExactly(DecisionGraph.AI_ASSESS);
    assertThat(DecisionGraph.isSkippable(DecisionGraph.AI_ASSESS)).isTrue();
    assertThat(DecisionGraph.isSkippable(DecisionGraph.ADJUST)).isFalse();
    assertThat(DecisionGraph.isSkippable(DecisionGraph.ARBITRATE)).isFalse();
    assertThat(DecisionGraph.isSkippable("FraudScreen")).isFalse();
  }
}
