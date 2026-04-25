package com.bone.studio.generator.application.usecase.standard;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.UpdateDataSourceCommand;
import com.bone.studio.generator.application.command.handler.UpdateDataSourceHandler;
import org.springframework.stereotype.Component;

@Component
@Capability(name = "updateDataSourceUseCase", description = "更新数据源用例", inputSchema = "{}", outputSchema = "{}")
public class UpdateDataSourceUseCase {

    private final UpdateDataSourceHandler updateDataSourceHandler;

    public UpdateDataSourceUseCase(UpdateDataSourceHandler updateDataSourceHandler) {
        this.updateDataSourceHandler = updateDataSourceHandler;
    }

    public String execute(UpdateDataSourceCommand command) {
        return updateDataSourceHandler.handle(command);
    }
}