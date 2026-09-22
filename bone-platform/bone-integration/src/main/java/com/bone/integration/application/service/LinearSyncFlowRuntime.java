package com.bone.integration.application.service;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 线性拓扑同步执行（INT-09，Camel 未启用时的默认运行时）。 */
@Service
@ConditionalOnProperty(
    prefix = "integration.camel",
    name = "execution-enabled",
    havingValue = "false",
    matchIfMissing = true)
@RequiredArgsConstructor
public class LinearSyncFlowRuntime implements FlowRuntime {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final FlowService flowService;
  private final FlowNodeExecutor flowNodeExecutor;

  @Override
  public void execute(IntegrationLog log, IntegrationFlow flow) {
    log.start();
    List<FlowNode> nodes = flowService.getFlowNodes(flow.getId());
    List<FlowConnection> connections = flowService.getFlowConnections(flow.getId());

    try {
      Object context = parseInput(log.getInputData());
      FlowNode current = findStartNode(nodes);
      int guard = nodes.size() + 2;

      while (current != null && current.getType() != NodeType.END && guard-- > 0) {
        context = flowNodeExecutor.execute(current, context);
        current = nextNode(current, connections, nodes);
      }

      if (current == null || current.getType() != NodeType.END) {
        log.fail("流程未到达结束节点");
      } else {
        log.complete(stringify(context));
      }
    } catch (DomainException ex) {
      log.fail(ex.getMessage());
    } catch (Exception ex) {
      log.fail(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
    }
  }

  private static FlowNode findStartNode(List<FlowNode> nodes) {
    return nodes.stream()
        .filter(n -> n.getType() == NodeType.START)
        .findFirst()
        .orElseThrow(() -> new DomainException("流程缺少开始节点"));
  }

  private static FlowNode nextNode(
      FlowNode current, List<FlowConnection> connections, List<FlowNode> nodes) {
    Optional<FlowConnection> link =
        connections.stream().filter(c -> current.getId().equals(c.getSourceNodeId())).findFirst();
    if (link.isEmpty()) {
      return null;
    }
    Long targetId = link.get().getTargetNodeId();
    return nodes.stream().filter(n -> targetId.equals(n.getId())).findFirst().orElse(null);
  }

  private static Object parseInput(String inputData) {
    if (inputData == null || inputData.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(inputData, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return inputData;
    }
  }

  private static String stringify(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      return String.valueOf(value);
    }
  }
}
