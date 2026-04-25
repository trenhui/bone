package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreateRoleCmd;
import com.bone.iam.domain.role.Role;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.core.usecase.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateRole",
    description = "创建新角色",
    inputSchema = "{\"name\": \"string\", \"description\": \"string\"}",
    outputSchema = "{\"roleId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5
)
@Component
@RequiredArgsConstructor
public class CreateRoleHandler {
    private final RoleRepository roleRepository;

    @Transactional
    public Long handle(CreateRoleCmd cmd) {
        String code = cmd.getCode();
        if (code == null || code.isBlank()) {
            code = cmd.getName() == null ? "" : cmd.getName().trim()
                    .replaceAll("\\s+", "_")
                    .toUpperCase();
        }
        Role role = Role.create(cmd.getName(), code, cmd.getDescription(), 1, cmd.getTenantId(), null);
        roleRepository.save(role);
        return role.getId();
    }
}
