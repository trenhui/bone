package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.CreateLogCmd;
import com.bone.system.application.command.handler.LogCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateLog",
    description = "标准日志创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateLogUseCase implements UseCaseExecutor<CreateLogCmd, Long> {

    private final LogCommandHandler logCommandHandler;

    @Override
    public Long execute(CreateLogCmd cmd) {
        return logCommandHandler.handle(cmd);
    }
}
