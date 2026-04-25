package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.handler.ConfigCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "DeleteConfig",
    description = "标准系统配置删除，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class DeleteConfigUseCase implements UseCaseExecutor<Long, Void> {

    private final ConfigCommandHandler configCommandHandler;

    @Override
    public Void execute(Long id) {
        configCommandHandler.delete(id);
        return null;
    }
}
