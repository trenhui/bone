package com.bone.iam.application.usecase.standard;

import com.bone.core.usecase.UseCase;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.iam.application.command.cmd.UpdatePermissionCmd;
import com.bone.iam.application.command.handler.UpdatePermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
        name = "UpdatePermission",
        description = "更新权限",
        transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdatePermissionUseCase implements UseCaseExecutor<UpdatePermissionCmd, Void> {
    private final UpdatePermissionHandler updatePermissionHandler;

    @Override
    public Void execute(UpdatePermissionCmd cmd) {
        updatePermissionHandler.handle(cmd);
        return null;
    }
}

