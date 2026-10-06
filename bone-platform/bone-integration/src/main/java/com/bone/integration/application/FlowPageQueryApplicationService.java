package com.bone.integration.application;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.FlowDto;
import com.bone.integration.application.query.qry.FlowPageQuery;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.valueobject.FlowStatus;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FlowPageQueryApplicationService {

  private final IntegrationFlowRepository flowRepository;

  @Transactional(readOnly = true)
  public PageResult<FlowDto> handle(FlowPageQuery qry) {
    FlowStatus status =
        qry.status() != null && !qry.status().isBlank() ? FlowStatus.valueOf(qry.status()) : null;
    PageResult<IntegrationFlow> result =
        flowRepository.findPage(qry.keyword(), status, qry.page(), qry.size());

    List<FlowDto> records =
        result.getRecords().stream()
            .map(
                flow ->
                    new FlowDto(
                        flow.getId(),
                        flow.getName(),
                        flow.getDescription(),
                        flow.getStatus().name(),
                        Collections.emptyList(),
                        Collections.emptyList()))
            .collect(Collectors.toList());

    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }
}
