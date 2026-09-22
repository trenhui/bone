package com.bone.integration.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQuery;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.model.connector.valueobject.ConnectorStatus;
import com.bone.integration.domain.model.connector.valueobject.ConnectorType;
import com.bone.integration.domain.repository.ConnectorRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConnectorPageQueryApplicationService {

  private final ConnectorRepository connectorRepository;

  @Transactional(readOnly = true)
  public PageResult<ConnectorDTO> handle(ConnectorPageQuery qry) {
    ConnectorType type =
        qry.type() != null && !qry.type().isBlank() ? ConnectorType.fromString(qry.type()) : null;
    ConnectorStatus status =
        qry.status() != null && !qry.status().isBlank()
            ? ConnectorStatus.valueOf(qry.status())
            : null;
    PageResult<Connector> result =
        connectorRepository.findPage(qry.keyword(), type, status, qry.pageNum(), qry.pageSize());

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
