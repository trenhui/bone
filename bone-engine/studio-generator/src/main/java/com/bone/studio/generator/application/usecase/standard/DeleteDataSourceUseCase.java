package com.bone.studio.generator.application.usecase.standard;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.DeleteDataSourceCommand;
import com.bone.studio.generator.application.command.handler.DeleteDataSourceHandler;
import org.springframework.stereotype.Component;

@Component
@Capability(name = "deleteDataSourceUseCase", description = "删除数据源用例", inputSchema = "{}", outputSchema = "{}")
public class DeleteDataSourceUseCase {

    private final DeleteDataSourceHandler deleteDataSourceHandler;

    public DeleteDataSourceUseCase(DeleteDataSourceHandler deleteDataSourceHandler) {
        this.deleteDataSourceHandler = deleteDataSourceHandler;
    }

    public void execute(DeleteDataSourceCommand command) {
        deleteDataSourceHandler.handle(command);
    }
}