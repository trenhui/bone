package com.bone.studio.generator.application.query.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.query.qry.DataSourceListQuery;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Capability(
    name = "getDataSourceList",
    description = "获取数据源列表",
    inputSchema = "{}",
    outputSchema = "{}")
public class DataSourceListHandler {

  private final DataSourceRepository dataSourceRepository;

  public DataSourceListHandler(DataSourceRepository dataSourceRepository) {
    this.dataSourceRepository = dataSourceRepository;
  }

  @Transactional(readOnly = true)
  public Page<DataSource> handle(DataSourceListQuery query) {
    // 简化实现，返回空的 Page 对象
    return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList());
  }
}
