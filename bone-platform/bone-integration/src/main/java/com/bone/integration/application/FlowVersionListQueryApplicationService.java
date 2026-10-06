package com.bone.integration.application;

import com.bone.integration.application.query.dto.FlowVersionDto;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FlowVersionListQueryApplicationService {

  private final IntegrationFlowRepository flowRepository;

  @Transactional(readOnly = true)
  public List<FlowVersionDto> handle(Long flowId) {
    IntegrationFlow flow = flowRepository.findById(flowId);
    if (flow == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_FOUND, flowId);
    }
    // MVP+：流程当前仅维护最新一版，返回当前版本作为唯一版本记录
    FlowVersionDto version =
        new FlowVersionDto(flow.getId(), 1, flow.getName(), flow.getStatus().name());
    return List.of(version);
  }
}
