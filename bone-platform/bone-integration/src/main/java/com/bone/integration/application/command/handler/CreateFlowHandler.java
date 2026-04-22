package com.bone.integration.application.command.handler;

import com.bone.integration.application.command.cmd.CreateFlowCmd;
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
public class CreateFlowHandler {
    private final IntegrationFlowRepository flowRepository;
    private final FlowNodeRepository nodeRepository;
    private final FlowConnectionRepository connectionRepository;
    private final FlowService flowService;

    @Transactional
    public Long handle(CreateFlowCmd cmd) {
        flowService.validateFlowName(cmd.name(), null);
        IntegrationFlow flow = IntegrationFlow.create(cmd.name(), cmd.description());
        IntegrationFlow savedFlow = flowRepository.save(flow);

        List<FlowNode> nodes = cmd.nodes().stream()
                .map(nodeCmd -> FlowNode.create(
                        savedFlow.getId(),
                        nodeCmd.name(),
                        NodeType.fromString(nodeCmd.type()),
                        nodeCmd.config(),
                        nodeCmd.positionX(),
                        nodeCmd.positionY()
                ))
                .toList();
        nodeRepository.saveAll(nodes);

        List<FlowConnection> connections = cmd.connections().stream()
                .map(connCmd -> FlowConnection.create(
                        savedFlow.getId(),
                        FlowNodeId.of(connCmd.sourceNodeId()),
                        FlowNodeId.of(connCmd.targetNodeId()),
                        connCmd.condition()
                ))
                .toList();
        connectionRepository.saveAll(connections);

        return savedFlow.getId().value();
    }
}