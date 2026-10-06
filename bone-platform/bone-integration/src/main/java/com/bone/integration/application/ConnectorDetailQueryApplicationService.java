package com.bone.integration.application;

import com.bone.integration.application.query.dto.ConnectorDto;
import com.bone.integration.application.query.qry.ConnectorDetailQuery;
import com.bone.integration.application.support.ConnectorSecretSupport;
import com.bone.integration.application.support.ConnectorSecretSupport.MaskedConfig;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
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
  private final ConnectorSecretSupport connectorSecretSupport;

  @Transactional(readOnly = true)
  public ConnectorDto handle(ConnectorDetailQuery query) {
    Connector connector = connectorRepository.findById(query.id());
    if (connector == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.CONNECTOR_NOT_FOUND, query.id());
    }
    MaskedConfig masked = connectorSecretSupport.maskForRead(connector.getConfig());
    return new ConnectorDto(
        connector.getId(),
        connector.getName(),
        connector.getType().name(),
        masked.config(),
        connector.getStatus().name(),
        masked.secretKeysConfigured());
  }
}
