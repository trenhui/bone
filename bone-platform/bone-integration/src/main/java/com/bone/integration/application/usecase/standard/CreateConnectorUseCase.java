package com.bone.integration.application.usecase.standard;

import com.bone.integration.application.command.cmd.CreateConnectorCmd;
import com.bone.integration.application.command.handler.CreateConnectorHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateConnector",
    description = "标准连接器创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateConnectorUseCase implements UseCaseExecutor<CreateConnectorCmd, Long> {

    private final CreateConnectorHandler createConnectorHandler;

    @Override
    public Long execute(CreateConnectorCmd cmd) {
        return createConnectorHandler.handle(cmd);
    }
}
