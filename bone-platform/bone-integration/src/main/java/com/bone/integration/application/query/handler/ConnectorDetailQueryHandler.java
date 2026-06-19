package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorDetailQuery;
import com.bone.integration.domain.connector.Connector;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 连接器详情查询处理器 */
@Component
public class ConnectorDetailQueryHandler {

  @Transactional(readOnly = true)
  public ConnectorDTO handle(ConnectorDetailQuery query) {
    Connector connector =
        QueryBuilder.from(Connector.class)
            .where(Connector::getId)
            .eq(query.id())
            .first()
            .orElse(null);
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
