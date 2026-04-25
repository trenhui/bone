package com.bone.iam.application.usecase.standard;

import com.bone.core.usecase.UseCase;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.iam.application.command.cmd.UpdateRoleCmd;
import com.bone.iam.application.command.handler.UpdateRoleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
        name = "UpdateRole",
        description = "更新角色",
        transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateRoleUseCase implements UseCaseExecutor<UpdateRoleCmd, Void> {
    private final UpdateRoleHandler updateRoleHandler;

    @Override
    public Void execute(UpdateRoleCmd cmd) {
        updateRoleHandler.handle(cmd);
        return null;
    }
}

