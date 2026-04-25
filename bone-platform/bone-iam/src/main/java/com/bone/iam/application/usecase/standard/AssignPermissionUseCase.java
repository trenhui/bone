package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.AssignPermissionCmd;
import com.bone.iam.application.command.handler.AssignPermissionHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "AssignPermission",
    description = "标准角色权限分配，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class AssignPermissionUseCase implements UseCaseExecutor<AssignPermissionCmd, Void> {

    private final AssignPermissionHandler assignPermissionHandler;

    @Override
    public Void execute(AssignPermissionCmd cmd) {
        assignPermissionHandler.handle(cmd);
        return null;
    }
}
