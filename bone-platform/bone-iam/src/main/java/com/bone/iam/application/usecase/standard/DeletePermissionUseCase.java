package com.bone.iam.application.usecase.standard;

import com.bone.core.usecase.UseCase;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.iam.application.command.handler.DeletePermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
        name = "DeletePermission",
        description = "删除权限",
        transactional = true
)
@Service
@RequiredArgsConstructor
public class DeletePermissionUseCase implements UseCaseExecutor<Long, Void> {
    private final DeletePermissionHandler deletePermissionHandler;

    @Override
    public Void execute(Long id) {
        deletePermissionHandler.handle(id);
        return null;
    }
}

