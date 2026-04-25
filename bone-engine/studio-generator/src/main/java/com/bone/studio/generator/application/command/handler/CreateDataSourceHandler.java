package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Capability(name = "createDataSource", description = "创建数据源", inputSchema = "{}", outputSchema = "{}")
public class CreateDataSourceHandler {

    private final DataSourceRepository dataSourceRepository;

    @Transactional
    public String handle(CreateDataSourceCommand command) {
        log.info("开始创建数据源, 命令参数: name={}, type={}, host={}, port={}, database={}",
                command.getName(), command.getType(), command.getHost(), command.getPort(), command.getDatabase());

        try {
            String id = UUID.randomUUID().toString();
            log.debug("生成数据源ID: {}", id);

            DataSource dataSource = DataSource.create(
                    id,
                    command.getName(),
                    command.getType(),
                    command.getHost(),
                    command.getPort(),
                    command.getDatabase(),
                    command.getUsername(),
                    command.getPassword()
            );
            log.debug("数据源对象创建完成: {}", dataSource);

            String result = dataSourceRepository.save(dataSource);
            log.info("数据源保存成功, 返回ID: {}", result);

            return result;
        } catch (Exception e) {
            log.error("创建数据源失败, name={}, error={}", command.getName(), e.getMessage(), e);
            throw e;
        }
    }
}
