package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.UpdateDataSourceCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(name = "updateDataSource", description = "更新数据源", inputSchema = "{}", outputSchema = "{}")
public class UpdateDataSourceHandler {

    private final DataSourceRepository dataSourceRepository;

    @Transactional
    public String handle(UpdateDataSourceCommand command) {
        Long id = StudioIds.parseRequired(command.getId());
        DataSource dataSource = dataSourceRepository.findById(id);
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + command.getId());
        }

        int port = Integer.parseInt(command.getPort().trim());
        dataSource.update(
                command.getName(),
                command.getType(),
                command.getHost(),
                port,
                command.getDatabase(),
                command.getUsername(),
                command.getPassword());

        dataSourceRepository.update(dataSource);
        return StudioIds.toExternal(id);
    }
}
