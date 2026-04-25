package com.bone.iam.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.core.usecase.Capability;
import com.bone.iam.application.command.cmd.UpdateRoleCmd;
import com.bone.iam.domain.role.Role;
import com.bone.iam.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "UpdateRole",
        description = "更新角色信息",
        inputSchema = "{\"id\":\"long\",\"name\":\"string\",\"description\":\"string\"}",
        outputSchema = "{\"success\": \"boolean\"}",
        idempotent = false,
        cost = 1,
        retryable = true,
        timeout = 5
)
@Component
@RequiredArgsConstructor
public class UpdateRoleHandler {
    private final RoleRepository roleRepository;

    @Transactional
    public void handle(UpdateRoleCmd cmd) {
        Role role = roleRepository.findById(cmd.getId());
        if (role == null) {
            throw new NotFoundException("角色不存在");
        }
        if (cmd.getDescription() != null) {
            role.update(cmd.getDescription());
        }
        roleRepository.update(role);
    }
}
