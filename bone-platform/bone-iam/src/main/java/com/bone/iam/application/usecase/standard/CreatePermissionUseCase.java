package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.CreatePermissionCmd;
import com.bone.iam.application.command.handler.CreatePermissionHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreatePermission",
    description = "标准权限创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreatePermissionUseCase implements UseCaseExecutor<CreatePermissionCmd, Long> {

    private final CreatePermissionHandler createPermissionHandler;

    @Override
    public Long execute(CreatePermissionCmd cmd) {
        return createPermissionHandler.handle(cmd);
    }
}
