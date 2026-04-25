package com.bone.integration.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.cmd.UpdateFlowCmd;
import com.bone.integration.domain.connector.FlowConnection;
import com.bone.integration.domain.connector.FlowNode;
import com.bone.integration.domain.connector.IntegrationFlow;
import com.bone.integration.domain.connector.vo.FlowNodeId;
import com.bone.integration.domain.connector.vo.NodeType;
import com.bone.integration.domain.repository.FlowConnectionRepository;
import com.bone.integration.domain.repository.FlowNodeRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.service.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Capability(
    name = "UpdateFlow",
    description = "更新集成流程配置",
    inputSchema = "{\"id\": \"long\", \"name\": \"string\", \"description\": \"string\", \"nodes\": \"array\", \"connections\": \"array\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 2,
    retryable = true,
    timeout = 30
)
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

        nodeRepository.deleteByFlowId(flow.getId());
        connectionRepository.deleteByFlowId(flow.getId());

        List<FlowNode> nodes = cmd.nodes().stream()
                .map(nodeCmd -> {
                    Long nodeId = DistributedIdGenerator.generateLongId();
                    return FlowNode.create(
                            nodeId,
                            flow.getId(),
                            nodeCmd.name(),
                            NodeType.fromString(nodeCmd.type()),
                            nodeCmd.config(),
                            nodeCmd.positionX(),
                            nodeCmd.positionY()
                    );
                })
                .toList();
        nodeRepository.saveAll(nodes);

        List<FlowConnection> connections = cmd.connections().stream()
                .map(connCmd -> {
                    Long connId = DistributedIdGenerator.generateLongId();
                    return FlowConnection.create(
                            connId,
                            flow.getId(),
                            FlowNodeId.of(connCmd.sourceNodeId()),
                            FlowNodeId.of(connCmd.targetNodeId()),
                            connCmd.condition()
                    );
                })
                .toList();
        connectionRepository.saveAll(connections);
    }
}
