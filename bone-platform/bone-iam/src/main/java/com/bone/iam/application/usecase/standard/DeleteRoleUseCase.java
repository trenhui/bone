package com.bone.iam.application.usecase.standard;

import com.bone.core.usecase.UseCase;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.iam.application.command.handler.DeleteRoleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
        name = "DeleteRole",
        description = "删除角色",
        transactional = true
)
@Service
@RequiredArgsConstructor
public class DeleteRoleUseCase implements UseCaseExecutor<Long, Void> {
    private final DeleteRoleHandler deleteRoleHandler;

    @Override
    public Void execute(Long id) {
        deleteRoleHandler.handle(id);
        return null;
    }
}

