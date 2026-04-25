package com.bone.studio.generator.application.usecase.standard;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.CreateDataSourceCommand;
import com.bone.studio.generator.application.command.handler.CreateDataSourceHandler;
import org.springframework.stereotype.Component;

@Component
@Capability(name = "createDataSourceUseCase", description = "创建数据源用例", inputSchema = "{}", outputSchema = "{}")
public class CreateDataSourceUseCase {

    private final CreateDataSourceHandler createDataSourceHandler;

    public CreateDataSourceUseCase(CreateDataSourceHandler createDataSourceHandler) {
        this.createDataSourceHandler = createDataSourceHandler;
    }

    public String execute(CreateDataSourceCommand command) {
        return createDataSourceHandler.handle(command);
    }
}