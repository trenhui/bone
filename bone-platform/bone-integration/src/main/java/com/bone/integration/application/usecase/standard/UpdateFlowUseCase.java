package com.bone.integration.application.usecase.standard;

import com.bone.integration.application.command.cmd.UpdateFlowCmd;
import com.bone.integration.application.command.handler.UpdateFlowHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "UpdateFlow",
    description = "标准集成流程更新，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateFlowUseCase implements UseCaseExecutor<UpdateFlowCmd, Void> {

    private final UpdateFlowHandler updateFlowHandler;

    @Override
    public Void execute(UpdateFlowCmd cmd) {
        updateFlowHandler.handle(cmd);
        return null;
    }
}
