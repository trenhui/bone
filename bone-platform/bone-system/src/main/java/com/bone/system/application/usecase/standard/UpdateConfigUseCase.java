package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.UpdateConfigCmd;
import com.bone.system.application.command.handler.ConfigCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "UpdateConfig",
    description = "标准系统配置更新，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateConfigUseCase implements UseCaseExecutor<UpdateConfigCmd, Void> {

    private final ConfigCommandHandler configCommandHandler;

    @Override
    public Void execute(UpdateConfigCmd cmd) {
        configCommandHandler.handle(cmd);
        return null;
    }
}
