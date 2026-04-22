package com.bone.integration.application.command.handler;

import com.bone.integration.application.command.cmd.UpdateFlowCmd;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.vo.FlowNodeId;
import com.bone.integration.domain.model.flow.vo.NodeType;
import com.bone.integration.domain.repository.FlowConnectionRepository;
import com.bone.integration.domain.repository.FlowNodeRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UpdateFlowHandler {
    private final IntegrationFlowRepository flowRepository;
    private final FlowNodeRepository nodeRepository;
    private final FlowConnectionRepository connectionRepository;
    private final FlowService flowService;

    @Transactional
    public void handle(UpdateFlowCmd cmd) {
        IntegrationFlow flow = flowRepository.findById(cmd.id())
                .orElseThrow(() -> new com.bone.core.exception.DomainException("流程不存在"));
        flowService.validateFlowName(cmd.name(), cmd.id());
        flow.update(cmd.name(), cmd.description());
        flowRepository.save(flow);

        // 删除旧的节点和连接
        nodeRepository.deleteByFlowId(flow.getId());
        connectionRepository.deleteByFlowId(flow.getId());

        // 保存新的节点
        List<FlowNode> nodes = cmd.nodes().stream()
                .map(nodeCmd -> FlowNode.create(
                        flow.getId(),
                        nodeCmd.name(),
                        NodeType.fromString(nodeCmd.type()),
                        nodeCmd.config(),
                        nodeCmd.positionX(),
                        nodeCmd.positionY()
                ))
                .toList();
        nodeRepository.saveAll(nodes);

        // 保存新的连接
        List<FlowConnection> connections = cmd.connections().stream()
                .map(connCmd -> FlowConnection.create(
                        flow.getId(),
                        FlowNodeId.of(connCmd.sourceNodeId()),
                        FlowNodeId.of(connCmd.targetNodeId()),
                        connCmd.condition()
                ))
                .toList();
        connectionRepository.saveAll(connections);
    }
}