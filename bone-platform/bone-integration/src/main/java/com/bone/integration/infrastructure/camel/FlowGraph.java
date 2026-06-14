package com.bone.integration.infrastructure.camel;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.model.flow.vo.NodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 流程图索引：节点与连线邻接表。 */
final class FlowGraph {

  private final Map<Long, FlowNode> nodesById;
  private final Map<Long, List<FlowConnection>> outgoing;

  private FlowGraph(Map<Long, FlowNode> nodesById, Map<Long, List<FlowConnection>> outgoing) {
    this.nodesById = nodesById;
    this.outgoing = outgoing;
  }

  static FlowGraph of(List<FlowNode> nodes, List<FlowConnection> connections) {
    Map<Long, FlowNode> nodesById = new HashMap<>();
    for (FlowNode node : nodes) {
      nodesById.put(node.getId(), node);
    }
    Map<Long, List<FlowConnection>> outgoing = new HashMap<>();
    for (FlowConnection connection : connections) {
      outgoing
          .computeIfAbsent(connection.getSourceNodeId(), ignored -> new ArrayList<>())
          .add(connection);
    }
    return new FlowGraph(nodesById, outgoing);
  }

  FlowNode requireNode(Long nodeId) {
    FlowNode node = nodesById.get(nodeId);
    if (node == null) {
      throw new DomainException("流程节点不存在: " + nodeId);
    }
    return node;
  }

  FlowNode findStartNode() {
    return nodesById.values().stream()
        .filter(n -> n.getType() == NodeType.START)
        .findFirst()
        .orElseThrow(() -> new DomainException("流程缺少开始节点"));
  }

  List<FlowConnection> outgoing(FlowNode node) {
    return outgoing.getOrDefault(node.getId(), List.of());
  }

  FlowNode singleNext(FlowNode node) {
    List<FlowConnection> links = outgoing(node);
    if (links.isEmpty()) {
      return null;
    }
    if (links.size() > 1) {
      throw new DomainException("节点存在多条出边，需使用分支编排: " + node.getName());
    }
    return requireNode(links.get(0).getTargetNodeId());
  }

  boolean isTerminal(FlowNode node) {
    return node.getType() == NodeType.END;
  }

  List<FlowNode> branchTargets(FlowNode decisionNode) {
    List<FlowNode> targets = new ArrayList<>();
    for (FlowConnection link : outgoing(decisionNode)) {
      targets.add(requireNode(link.getTargetNodeId()));
    }
    if (targets.isEmpty()) {
      throw new DomainException("分支节点缺少出边: " + decisionNode.getName());
    }
    return targets;
  }

  String branchCondition(FlowConnection connection) {
    return Objects.requireNonNullElse(connection.getCondition(), "true");
  }
}
