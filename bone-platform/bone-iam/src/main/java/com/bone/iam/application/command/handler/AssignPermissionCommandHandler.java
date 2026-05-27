package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.AssignPermissionCommand;
import com.bone.iam.application.service.RolePermissionBindingService;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.core.capability.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "AssignPermission",
    description = "为角色分配权限",
    inputSchema = "{\"roleId\": \"long\", \"permissionIds\": [\"long\"]}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 5
)
@Component
@RequiredArgsConstructor
public class AssignPermissionCommandHandler {

    private final RoleRepository roleRepository;
    private final RolePermissionBindingService rolePermissionBindingService;

    @Transactional
    public void handle(AssignPermissionCommand cmd) {
        if (cmd == null || cmd.getRoleId() == null) {
            throw new IllegalArgumentException("角色 ID 不能为空");
        }
        if (roleRepository.findById(cmd.getRoleId()) == null) {
            throw new RuntimeException("角色不存在: " + cmd.getRoleId());
        }
        rolePermissionBindingService.replaceBindings(cmd.getRoleId(), cmd.getPermissionIds());
    }
}
