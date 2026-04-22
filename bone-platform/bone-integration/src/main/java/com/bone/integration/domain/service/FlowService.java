package com.bone.integration.domain.service;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.integration.domain.repository.FlowConnectionRepository;
import com.bone.integration.domain.repository.FlowNodeRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowService {
    private final IntegrationFlowRepository flowRepository;
    private final FlowNodeRepository nodeRepository;
    private final FlowConnectionRepository connectionRepository;

    public void validateFlowName(String name, Long excludeId) {
        if (flowRepository.existsByName(name)) {
            IntegrationFlow existing = flowRepository.findByName(name);
            if (excludeId == null || !existing.getId().value().equals(excludeId)) {
                throw new DomainException("流程名称已存在");
            }
        }
    }

    public List<FlowNode> getFlowNodes(FlowId flowId) {
        return nodeRepository.findByFlowId(flowId);
    }

    public List<FlowConnection> getFlowConnections(FlowId flowId) {
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
        // 检查是否有开始节点
        boolean hasStartNode = nodes.stream().anyMatch(node -> node.getType().name().equals("START"));
        if (!hasStartNode) {
            throw new DomainException("流程必须包含开始节点");
        }
        // 检查是否有结束节点
        boolean hasEndNode = nodes.stream().anyMatch(node -> node.getType().name().equals("END"));
        if (!hasEndNode) {
            throw new DomainException("流程必须包含结束节点");
        }
    }
}