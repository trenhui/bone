package com.bone.integration.application.support;

import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.FlowConnectionRepository;
import com.bone.integration.domain.repository.FlowNodeRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationFlowSupport")
@RequiredArgsConstructor
public class FlowSupport {
  private final IntegrationFlowRepository flowRepository;
  private final FlowNodeRepository nodeRepository;
  private final FlowConnectionRepository connectionRepository;

  public void validateFlowName(String name, Long excludeId) {
    IntegrationFlow existing = flowRepository.findByName(name);
    if (existing != null && (excludeId == null || !excludeId.equals(existing.getId()))) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NAME_CONFLICT, name);
    }
  }

  public List<FlowNode> getFlowNodes(Long flowId) {
    return nodeRepository.findByFlowId(flowId);
  }

  public List<FlowConnection> getFlowConnections(Long flowId) {
    return connectionRepository.findByFlowId(flowId);
  }

  public void validateFlow(IntegrationFlow flow) {
    if (!flow.getStatus().isActive()) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_ACTIVE, flow.getId());
    }
    List<FlowNode> nodes = nodeRepository.findByFlowId(flow.getId());
    if (nodes.isEmpty()) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NODES_EMPTY, flow.getId());
    }
    boolean hasStartNode = nodes.stream().anyMatch(node -> node.getType().name().equals("START"));
    if (!hasStartNode) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_START_NODE_MISSING, flow.getId());
    }
    boolean hasEndNode = nodes.stream().anyMatch(node -> node.getType().name().equals("END"));
    if (!hasEndNode) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_END_NODE_MISSING, flow.getId());
    }
  }

  public void deleteNodesByFlowId(Long flowId) {
    List<FlowNode> nodes = nodeRepository.findByFlowId(flowId);
    if (!nodes.isEmpty()) {
      nodeRepository.deleteByIds(nodes.stream().map(FlowNode::getId).toList());
    }
  }

  public void deleteConnectionsByFlowId(Long flowId) {
    List<FlowConnection> connections = connectionRepository.findByFlowId(flowId);
    if (!connections.isEmpty()) {
      connectionRepository.deleteByIds(connections.stream().map(FlowConnection::getId).toList());
    }
  }
}
