package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.CreateConfigCmd;
import com.bone.system.application.command.handler.ConfigCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateConfig",
    description = "标准系统配置创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateConfigUseCase implements UseCaseExecutor<CreateConfigCmd, Long> {

    private final ConfigCommandHandler configCommandHandler;

    @Override
    public Long execute(CreateConfigCmd cmd) {
        return configCommandHandler.handle(cmd);
    }
}
