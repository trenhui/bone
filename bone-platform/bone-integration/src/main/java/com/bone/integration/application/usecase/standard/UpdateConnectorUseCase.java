package com.bone.integration.application.usecase.standard;

import com.bone.integration.application.command.cmd.UpdateConnectorCmd;
import com.bone.integration.application.command.handler.UpdateConnectorHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "UpdateConnector",
    description = "标准连接器更新，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateConnectorUseCase implements UseCaseExecutor<UpdateConnectorCmd, Void> {

    private final UpdateConnectorHandler updateConnectorHandler;

    @Override
    public Void execute(UpdateConnectorCmd cmd) {
        updateConnectorHandler.handle(cmd);
        return null;
    }
}
