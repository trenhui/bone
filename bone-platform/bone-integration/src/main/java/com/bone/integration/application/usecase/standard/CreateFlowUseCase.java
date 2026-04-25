package com.bone.integration.application.usecase.standard;

import com.bone.integration.application.command.cmd.CreateFlowCmd;
import com.bone.integration.application.command.handler.CreateFlowHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateFlow",
    description = "标准集成流程创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateFlowUseCase implements UseCaseExecutor<CreateFlowCmd, Long> {

    private final CreateFlowHandler createFlowHandler;

    @Override
    public Long execute(CreateFlowCmd cmd) {
        return createFlowHandler.handle(cmd);
    }
}
