package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Capability(name = "createDataSource", description = "创建数据源", inputSchema = "{}", outputSchema = "{}")
public class CreateDataSourceHandler {

    private final DataSourceRepository dataSourceRepository;

    @Transactional
    public String handle(CreateDataSourceCommand command) {
        log.info("创建数据源 name={}, type={}, host={}", command.getName(), command.getType(), command.getHost());
        Long id = DistributedIdGenerator.generateLongId();
        int port = Integer.parseInt(command.getPort().trim());
        DataSource dataSource = DataSource.create(
                id,
                0L,
                command.getName(),
                command.getType(),
                command.getHost(),
                port,
                command.getDatabase(),
                command.getUsername(),
                command.getPassword());
        dataSourceRepository.insert(dataSource);
        return StudioIds.toExternal(id);
    }
}
