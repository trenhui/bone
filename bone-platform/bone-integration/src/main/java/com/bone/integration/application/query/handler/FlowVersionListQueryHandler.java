package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.FlowVersionDTO;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FlowVersionListQueryHandler {

  private final IntegrationFlowRepository flowRepository;

  @Transactional(readOnly = true)
  public List<FlowVersionDTO> handle(Long flowId) {
    IntegrationFlow flow = flowRepository.findById(flowId);
    if (flow == null) {
      throw new DomainException("流程不存在");
    }
    // MVP+：流程当前仅维护最新一版，返回当前版本作为唯一版本记录
    FlowVersionDTO version =
        new FlowVersionDTO(flow.getId(), 1, flow.getName(), flow.getStatus().name());
    return List.of(version);
  }
}
