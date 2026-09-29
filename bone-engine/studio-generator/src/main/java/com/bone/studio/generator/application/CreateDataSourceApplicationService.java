package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Capability(
    name = "createDataSource",
    description = "创建数据源",
    inputSchema = "{}",
    outputSchema = "{}")
public class CreateDataSourceApplicationService {

  private final DataSourceRepository dataSourceRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public String handle(CreateDataSourceCommand command) {
    log.info(
        "创建数据源 name={}, type={}, host={}", command.getName(), command.getType(), command.getHost());
    int port = Integer.parseInt(command.getPort().trim());
    // 租户一律取自可信上下文；缺失即失败关闭，不再写死平台租户 0L。
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(GeneratorErrorCodes.TENANT_CONTEXT_MISSING, command.getName());
    }
    DataSource dataSource =
        DataSource.create(
            null,
            tenantId,
            command.getName(),
            command.getType(),
            command.getHost(),
            port,
            command.getDatabase(),
            command.getUsername(),
            command.getPassword());
    // 分布式 ID 由 SDK 统一生成（insert/save 均会重新生成主键），以返回值为准回传
    Long id = dataSourceRepository.insert(dataSource);
    return StudioIds.toExternal(id);
  }
}
