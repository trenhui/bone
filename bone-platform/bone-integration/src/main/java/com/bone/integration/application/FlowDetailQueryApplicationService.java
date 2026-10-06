package com.bone.integration.application;

import com.bone.integration.application.query.dto.FlowDetailDto;
import com.bone.integration.application.query.qry.FlowDetailQuery;
import com.bone.integration.application.support.FlowSupport;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
import com.bone.integration.domain.model.flow.FlowConnection;
import com.bone.integration.domain.model.flow.FlowNode;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 流程详情查询处理器 */
@Component
@RequiredArgsConstructor
public class FlowDetailQueryApplicationService {
  private final IntegrationFlowRepository flowRepository;
  private final FlowSupport flowSupport;

  @Transactional(readOnly = true)
  public FlowDetailDto handle(FlowDetailQuery qry) {
    IntegrationFlow flow = flowRepository.findById(qry.id());
    if (flow == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_FOUND, qry.id());
    }
    List<FlowNode> nodes = flowSupport.getFlowNodes(flow.getId());
    List<FlowConnection> connections = flowSupport.getFlowConnections(flow.getId());

    List<FlowDetailDto.FlowNodeDto> nodeDtos =
        nodes.stream()
            .map(
                node ->
                    new FlowDetailDto.FlowNodeDto(
                        node.getId(),
                        node.getName(),
                        node.getType().name(),
                        node.getConfig(),
                        node.getPositionX(),
                        node.getPositionY()))
            .collect(Collectors.toList());

    List<FlowDetailDto.FlowConnectionDTO> connectionDtos =
        connections.stream()
            .map(
                conn ->
                    new FlowDetailDto.FlowConnectionDTO(
                        conn.getId(),
                        conn.getSourceNodeId(),
                        conn.getTargetNodeId(),
                        conn.getCondition()))
            .collect(Collectors.toList());

    return new FlowDetailDto(
        flow.getId(),
        flow.getName(),
        flow.getDescription(),
        flow.getStatus().name(),
        nodeDtos,
        connectionDtos);
  }
}
