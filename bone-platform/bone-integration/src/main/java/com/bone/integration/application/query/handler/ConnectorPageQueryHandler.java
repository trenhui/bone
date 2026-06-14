package com.bone.integration.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQuery;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.model.connector.vo.ConnectorStatus;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConnectorPageQueryHandler {

  @Transactional(readOnly = true)
  public PageResult<ConnectorDTO> handle(ConnectorPageQuery qry) {
    FluentQuery<Connector> query = QueryBuilder.from(Connector.class);

    if (qry.keyword() != null && !qry.keyword().isBlank()) {
      query.where(Connector::getName).like(qry.keyword());
    }
    if (qry.type() != null && !qry.type().isBlank()) {
      query.where(Connector::getType).eq(ConnectorType.fromString(qry.type()));
    }
    if (qry.status() != null && !qry.status().isBlank()) {
      query.where(Connector::getStatus).eq(ConnectorStatus.valueOf(qry.status()));
    }

    PageResult<Connector> result =
        query.orderByDesc(Connector::getId).page(qry.pageNum(), qry.pageSize());

    List<ConnectorDTO> records =
        result.getRecords().stream()
            .map(
                c ->
                    new ConnectorDTO(
                        c.getId(),
                        c.getName(),
                        c.getType().name(),
                        c.getConfig(),
                        c.getStatus().name()))
            .collect(Collectors.toList());

    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }
}
