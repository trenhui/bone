package com.bone.claim.domain.model.decision;

import com.bone.core.exception.DomainException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 决策图定义（版本化资产，§7.3）。
 *
 * <p>一期为固定 5 节点主链：{@code EvidenceLoad → RuleCheck → AiAssess → Adjust → Arbitrate}， 对外 {@code
 * node_type} 仅 4 类（OCR/RULE/LLM/DECISION）。节点链路固定为"证据 → 解析 → 核责 → 理算 → 决策"， 其中 {@code
 * Adjust}（理算，确定性）置于 {@code AiAssess}（AI 建议）之后——AI 只产建议，金额永远由确定性代码算。
 *
 * <p>治理纪律：拓扑是版本化资产，变更须发版号并走 Shadow 比价 + 规则回归门禁，禁止热改线上运行图。 本类只做结构校验（无环 / 节点数上限 / 引用完整）。
 */
public final class DecisionGraph {

  public static final String EVIDENCE_LOAD = "EvidenceLoad";
  public static final String RULE_CHECK = "RuleCheck";
  public static final String AI_ASSESS = "AiAssess";
  public static final String ADJUST = "Adjust";
  public static final String ARBITRATE = "Arbitrate";

  /** 节点数上限（§7.3：节点 ≤5 类）。 */
  public static final int MAX_NODES = 5;

  /** 决策节点（{@code next} 为后继节点名，null 表示链尾）。 */
  public record Node(String name, DecisionNodeType type, String next) {}

  private final List<Node> nodes;

  private DecisionGraph(List<Node> nodes) {
    this.nodes = List.copyOf(nodes);
  }

  public static DecisionGraph of(List<Node> nodes) {
    DecisionGraph graph = new DecisionGraph(new ArrayList<>(nodes));
    graph.validate();
    return graph;
  }

  /** 标准 5 节点主链。 */
  public static DecisionGraph canonical() {
    return of(
        List.of(
            new Node(EVIDENCE_LOAD, DecisionNodeType.OCR, RULE_CHECK),
            new Node(RULE_CHECK, DecisionNodeType.RULE, AI_ASSESS),
            new Node(AI_ASSESS, DecisionNodeType.LLM, ADJUST),
            new Node(ADJUST, DecisionNodeType.RULE, ARBITRATE),
            new Node(ARBITRATE, DecisionNodeType.DECISION, null)));
  }

  public List<Node> nodes() {
    return nodes;
  }

  public Node node(String name) {
    return nodes.stream()
        .filter(n -> n.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new DomainException("未知决策节点: " + name));
  }

  /**
   * 结构校验：无环（决策图必须是 DAG）、节点数 ≤5、节点名唯一、后继引用完整。
   *
   * <p>环检测在定义期做（而非运行期），配置错误早暴露——与责任链"配置校验期 DAG 检测"同一纪律。
   */
  public void validate() {
    if (nodes.isEmpty()) {
      throw new DomainException("决策图不能为空");
    }
    if (nodes.size() > MAX_NODES) {
      throw new DomainException("决策图节点数超限（≤" + MAX_NODES + "）: " + nodes.size());
    }
    Set<String> names = new HashSet<>();
    for (Node n : nodes) {
      if (!names.add(n.name())) {
        throw new DomainException("决策图节点名重复: " + n.name());
      }
    }
    for (Node n : nodes) {
      if (n.next() != null && !names.contains(n.next())) {
        throw new DomainException("决策图后继节点不存在: " + n.name() + " → " + n.next());
      }
    }
    for (Node start : nodes) {
      Set<String> visited = new HashSet<>();
      Node cur = start;
      while (cur != null) {
        if (!visited.add(cur.name())) {
          throw new DomainException("决策图存在环: " + String.join("→", visited) + "→" + cur.name());
        }
        cur = cur.next() == null ? null : node(cur.next());
      }
    }
  }
}
