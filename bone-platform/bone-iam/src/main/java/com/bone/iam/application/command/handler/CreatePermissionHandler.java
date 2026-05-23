package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.core.capability.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreatePermission",
    description = "创建新权限",
    inputSchema = "{\"code\": \"string\", \"name\": \"string\", \"description\": \"string\"}",
    outputSchema = "{\"permissionId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5
)
@Component
@RequiredArgsConstructor
public class CreatePermissionHandler {
    private final PermissionRepository permissionRepository;

    @Transactional
    public Long handle(CreatePermissionCommand cmd) {
        Permission permission = Permission.create(cmd.getCode(), cmd.getName(), cmd.getDescription(),
                                                   cmd.getResourceType(), cmd.getResourcePath(), cmd.getAction(),
                                                   cmd.getParentId(), cmd.getType(), cmd.getSortOrder());
        permissionRepository.save(permission);
        return permission.getId();
    }
}
