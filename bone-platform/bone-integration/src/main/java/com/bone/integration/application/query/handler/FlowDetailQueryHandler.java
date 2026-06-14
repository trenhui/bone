package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.FlowDetailDTO;
import com.bone.integration.application.query.qry.FlowDetailQuery;
import com.bone.integration.application.service.FlowService;
import com.bone.integration.domain.flow.FlowConnection;
import com.bone.integration.domain.flow.FlowNode;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 流程详情查询处理器 */
@Component
@RequiredArgsConstructor
public class FlowDetailQueryHandler {
  private final IntegrationFlowRepository flowRepository;
  private final FlowService flowService;

  @Transactional(readOnly = true)
  public FlowDetailDTO handle(FlowDetailQuery qry) {
    IntegrationFlow flow = flowRepository.findById(qry.id());
    if (flow == null) {
      throw new DomainException("流程不存在");
    }
    List<FlowNode> nodes = flowService.getFlowNodes(flow.getId());
    List<FlowConnection> connections = flowService.getFlowConnections(flow.getId());

    List<FlowDetailDTO.FlowNodeDTO> nodeDTOs =
        nodes.stream()
            .map(
                node ->
                    new FlowDetailDTO.FlowNodeDTO(
                        node.getId(),
                        node.getName(),
                        node.getType().name(),
                        node.getConfig(),
                        node.getPositionX(),
                        node.getPositionY()))
            .collect(Collectors.toList());

    List<FlowDetailDTO.FlowConnectionDTO> connectionDTOs =
        connections.stream()
            .map(
                conn ->
                    new FlowDetailDTO.FlowConnectionDTO(
                        conn.getId(),
                        conn.getSourceNodeId(),
                        conn.getTargetNodeId(),
                        conn.getCondition()))
            .collect(Collectors.toList());

    return new FlowDetailDTO(
        flow.getId(),
        flow.getName(),
        flow.getDescription(),
        flow.getStatus().name(),
        nodeDTOs,
        connectionDTOs);
  }
}
