package com.bone.studio.generator.application.query.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.query.qry.DataSourceTablesQuery;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.gateway.DatabaseMetadataGateway;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(
    name = "getDataSourceTables",
    description = "获取数据源表列表",
    inputSchema = "{}",
    outputSchema = "{}")
public class DataSourceTablesApplicationService {

  private final DataSourceRepository dataSourceRepository;
  private final DatabaseMetadataGateway metadataGateway;

  @Transactional(readOnly = true)
  public List<DatabaseTable> handle(DataSourceTablesQuery query) {
    Long id = StudioIds.parseRequired(query.getDataSourceId());
    DataSource dataSource = dataSourceRepository.findById(id);
    if (dataSource == null) {
      throw new IllegalArgumentException("数据源不存在: " + query.getDataSourceId());
    }
    return metadataGateway.loadTables(dataSource);
  }
}
