package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "DeleteRole",
        description = "删除角色（逻辑删除）",
        inputSchema = "{\"id\": \"long\"}",
        outputSchema = "{\"success\": \"boolean\"}",
        idempotent = true,
        cost = 1,
        retryable = true,
        timeout = 5
)
@Component
@RequiredArgsConstructor
public class DeleteRoleHandler {
    private final RoleRepository roleRepository;

    @Transactional
    public void handle(Long id) {
        roleRepository.deleteById(id);
    }
}

