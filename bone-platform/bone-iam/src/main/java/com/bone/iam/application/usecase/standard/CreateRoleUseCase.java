package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.CreateRoleCmd;
import com.bone.iam.application.command.handler.CreateRoleHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateRole",
    description = "标准角色创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateRoleUseCase implements UseCaseExecutor<CreateRoleCmd, Long> {

    private final CreateRoleHandler createRoleHandler;

    @Override
    public Long execute(CreateRoleCmd cmd) {
        return createRoleHandler.handle(cmd);
    }
}
