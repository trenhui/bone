package com.bone.studio.generator.application.query.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.query.qry.DataSourceByIdQuery;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Capability(
    name = "getDataSourceById",
    description = "根据ID获取数据源",
    inputSchema = "{}",
    outputSchema = "{}")
public class DataSourceByIdHandler {

  private final DataSourceRepository dataSourceRepository;

  public DataSourceByIdHandler(DataSourceRepository dataSourceRepository) {
    this.dataSourceRepository = dataSourceRepository;
  }

  @Transactional(readOnly = true)
  public DataSource handle(DataSourceByIdQuery query) {
    Long id = StudioIds.parseRequired(query.getId());
    DataSource dataSource = dataSourceRepository.findById(id);
    if (dataSource == null) {
      throw new IllegalArgumentException("数据源不存在: " + query.getId());
    }
    return dataSource;
  }
}
