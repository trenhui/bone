package com.bone.studio.generator.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.TestDataSourceConnectionCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.gateway.DatabaseMetadataGateway;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Capability(
    name = "testDataSourceConnection",
    description = "测试数据源连接",
    inputSchema = "{}",
    outputSchema = "{}")
public class TestDataSourceConnectionApplicationService {

  private final DataSourceRepository dataSourceRepository;
  private final DatabaseMetadataGateway metadataGateway;

  public boolean handle(TestDataSourceConnectionCommand command) {
    DataSource dataSource = dataSourceRepository.findById(StudioIds.parseRequired(command.getId()));
    if (dataSource == null) {
      throw new IllegalArgumentException("数据源不存在: " + command.getId());
    }
    return metadataGateway.testConnection(dataSource);
  }
}
