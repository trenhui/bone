package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreatePermissionCmd;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.permission.vo.PermissionCode;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreatePermissionHandler {
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;

    @Transactional
    public Long handle(CreatePermissionCmd cmd) {
        if (permissionService.isPermissionCodeExists(cmd.getCode())) {
            throw new RuntimeException("权限代码已存在");
        }

        PermissionCode code = PermissionCode.of(cmd.getCode());
        String parentId = cmd.getParentId() != null ? String.valueOf(cmd.getParentId()) : null;
        Permission permission = Permission.create(code, cmd.getName(), cmd.getDescription(),
                                               parentId, cmd.getType());
        permissionRepository.save(permission);
        return permission.getDbId();
    }
}