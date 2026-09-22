package com.bone.integration.application;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorDetailQuery;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 连接器详情查询。 */
@Component
@RequiredArgsConstructor
public class ConnectorDetailQueryApplicationService {

  private final ConnectorRepository connectorRepository;

  @Transactional(readOnly = true)
  public ConnectorDTO handle(ConnectorDetailQuery query) {
    Connector connector = connectorRepository.findById(query.id());
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }
    return new ConnectorDTO(
        connector.getId(),
        connector.getName(),
        connector.getType().name(),
        connector.getConfig(),
        connector.getStatus().name());
  }
}
