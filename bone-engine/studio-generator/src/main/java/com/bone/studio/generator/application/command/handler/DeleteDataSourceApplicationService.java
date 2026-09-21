package com.bone.studio.generator.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.DeleteDataSourceCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(
    name = "deleteDataSource",
    description = "删除数据源",
    inputSchema = "{}",
    outputSchema = "{}")
public class DeleteDataSourceApplicationService {

  private final DataSourceRepository dataSourceRepository;

  @Transactional
  public boolean handle(DeleteDataSourceCommand command) {
    return dataSourceRepository.deleteById(StudioIds.parseRequired(command.getId()));
  }
}
