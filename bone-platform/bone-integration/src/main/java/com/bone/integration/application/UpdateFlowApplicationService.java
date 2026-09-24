package com.bone.integration.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.command.cmd.UpdateFlowCommand;
import com.bone.integration.application.support.FlowSupport;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import com.bone.integration.domain.repository.FlowConnectionRepository;
import com.bone.integration.domain.repository.FlowNodeRepository;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateFlow",
    description = "更新集成流程配置",
    inputSchema =
        "{\"id\": \"long\", \"name\": \"string\", \"description\": \"string\", \"nodes\": \"array\", \"connections\": \"array\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 2,
    retryable = true,
    timeout = 30)
/**
 * 更新集成流程配置。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：流程状态迁移由 integration 的 Outbox 在基础设施层发布集成事件， 聚合本身不发布 Bone
 * 领域事件；故按 E-5.4（集成事件由 Outbox 另管）声明豁免。 若将来需聚合级领域事件，须改为调用 publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
public class UpdateFlowApplicationService {
  private final IntegrationFlowRepository flowRepository;
  private final FlowNodeRepository nodeRepository;
  private final FlowConnectionRepository connectionRepository;
  private final FlowSupport flowSupport;

  @Transactional
  public void handle(UpdateFlowCommand cmd) {
    IntegrationFlow flow = flowRepository.findById(cmd.id());
    if (flow == null) {
      throw new DomainException("流程不存在");
    }
    flowSupport.validateFlowName(cmd.name(), cmd.id());
    flow.update(cmd.name(), cmd.description());
    flowRepository.save(flow);

    flowSupport.deleteNodesByFlowId(flow.getId());
    flowSupport.deleteConnectionsByFlowId(flow.getId());

    List<FlowNode> nodes =
        cmd.nodes().stream()
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
            .toList();
    nodeRepository.batchSave(nodes);

    List<FlowConnection> connections =
        cmd.connections().stream()
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
            .toList();
    connectionRepository.batchSave(connections);
  }
}
