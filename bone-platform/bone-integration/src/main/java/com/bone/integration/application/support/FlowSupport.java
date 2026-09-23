package com.bone.integration.application.support;

import com.bone.core.exception.DomainException;
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
      throw new DomainException("流程名称已存在");
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
      throw new DomainException("流程未激活");
    }
    List<FlowNode> nodes = nodeRepository.findByFlowId(flow.getId());
    if (nodes.isEmpty()) {
      throw new DomainException("流程节点为空");
    }
    boolean hasStartNode = nodes.stream().anyMatch(node -> node.getType().name().equals("START"));
    if (!hasStartNode) {
      throw new DomainException("流程必须包含开始节点");
    }
    boolean hasEndNode = nodes.stream().anyMatch(node -> node.getType().name().equals("END"));
    if (!hasEndNode) {
      throw new DomainException("流程必须包含结束节点");
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
