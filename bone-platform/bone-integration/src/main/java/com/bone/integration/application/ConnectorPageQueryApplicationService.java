package com.bone.integration.application;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.ConnectorDto;
import com.bone.integration.application.query.qry.ConnectorPageQuery;
import com.bone.integration.application.support.ConnectorSecretSupport;
import com.bone.integration.application.support.ConnectorSecretSupport.MaskedConfig;
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
  private final ConnectorSecretSupport connectorSecretSupport;

  @Transactional(readOnly = true)
  public PageResult<ConnectorDto> handle(ConnectorPageQuery qry) {
    ConnectorType type =
        qry.type() != null && !qry.type().isBlank() ? ConnectorType.fromString(qry.type()) : null;
    ConnectorStatus status =
        qry.status() != null && !qry.status().isBlank()
            ? ConnectorStatus.valueOf(qry.status())
            : null;
    PageResult<Connector> result =
        connectorRepository.findPage(qry.keyword(), type, status, qry.page(), qry.size());

    // 列表同样剔除凭据：翻页即可批量导出全部连接器的第三方密钥，从读端绕过等于零成本泄露。
    List<ConnectorDto> records =
        result.getRecords().stream()
            .map(
                c -> {
                  MaskedConfig masked = connectorSecretSupport.maskForRead(c.getConfig());
                  return new ConnectorDto(
                      c.getId(),
                      c.getName(),
                      c.getType().name(),
                      masked.config(),
                      c.getStatus().name(),
                      masked.secretKeysConfigured());
                })
            .collect(Collectors.toList());

    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }
}
