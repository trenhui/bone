package com.bone.integration.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.cmd.CreateFlowCommand;
import com.bone.integration.application.event.IntegrationDomainEventPublisher;
import com.bone.integration.application.service.FlowService;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import com.bone.integration.domain.repository.FlowConnectionRepository;
import com.bone.integration.domain.repository.FlowNodeRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateFlow",
    description = "创建新的集成流程",
    inputSchema =
        "{\"name\": \"string\", \"description\": \"string\", \"nodes\": \"array\", \"connections\": \"array\"}",
    outputSchema = "{\"flowId\": \"long\"}",
    idempotent = false,
    cost = 3,
    retryable = true,
    timeout = 30)
@Component
@RequiredArgsConstructor
public class CreateFlowApplicationService {
  private final IntegrationFlowRepository flowRepository;
  private final FlowNodeRepository nodeRepository;
  private final FlowConnectionRepository connectionRepository;
  private final FlowService flowService;
  private final IntegrationDomainEventPublisher domainEventPublisher;

  @Transactional
  public Long handle(CreateFlowCommand cmd) {
    flowService.validateFlowName(cmd.name(), null);
    Long flowId = DistributedIdGenerator.generateLongId();
    IntegrationFlow flow = IntegrationFlow.create(flowId, cmd.name(), cmd.description());
    flowRepository.save(flow);

    List<FlowNode> nodes =
        cmd.nodes() != null
            ? cmd.nodes().stream()
                .map(
                    nodeCmd -> {
                      Long nodeId = DistributedIdGenerator.generateLongId();
                      return FlowNode.create(
                          nodeId,
                          flow.getId(),
                          nodeCmd.name(),
                          NodeType.fromString(nodeCmd.type()),
                          nodeCmd.config(),
                          nodeCmd.positionX(),
                          nodeCmd.positionY());
                    })
                .toList()
            : List.of();
    if (!nodes.isEmpty()) {
      nodeRepository.batchSave(nodes);
    }

    List<FlowConnection> connections =
        cmd.connections() != null
            ? cmd.connections().stream()
                .map(
                    connCmd -> {
                      Long connId = DistributedIdGenerator.generateLongId();
                      return FlowConnection.create(
                          connId,
                          flow.getId(),
                          connCmd.sourceNodeId(),
                          connCmd.targetNodeId(),
                          connCmd.condition());
                    })
                .toList()
            : List.of();
    if (!connections.isEmpty()) {
      connectionRepository.batchSave(connections);
    }

    domainEventPublisher.publishFrom(flow);
    return flow.getId();
  }
}
