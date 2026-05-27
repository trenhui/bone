package com.bone.iam.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.core.capability.Capability;
import com.bone.iam.application.command.cmd.UpdatePermissionCommand;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "UpdatePermission",
        description = "更新权限",
        inputSchema = "{\"id\":\"long\"}",
        outputSchema = "{\"success\": \"boolean\"}",
        idempotent = false,
        cost = 1,
        retryable = true,
        timeout = 5
)
@Component
@RequiredArgsConstructor
public class UpdatePermissionCommandHandler {
    private final PermissionRepository permissionRepository;

    @Transactional
    public void handle(UpdatePermissionCommand cmd) {
        Permission permission = permissionRepository.findById(cmd.getId());
        if (permission == null) {
            throw new NotFoundException("权限不存在");
        }
        permission.update(
                cmd.getName(),
                cmd.getDescription(),
                cmd.getResourceType(),
                cmd.getResourcePath(),
                cmd.getAction(),
                cmd.getParentId(),
                cmd.getType(),
                cmd.getSortOrder() == null ? 0 : cmd.getSortOrder()
        );
        permissionRepository.update(permission);
    }
}
