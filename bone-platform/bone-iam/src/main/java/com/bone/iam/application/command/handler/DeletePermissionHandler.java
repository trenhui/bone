package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "DeletePermission",
        description = "删除权限（逻辑删除）",
        inputSchema = "{\"id\": \"long\"}",
        outputSchema = "{\"success\": \"boolean\"}",
        idempotent = true,
        cost = 1,
        retryable = true,
        timeout = 5
)
@Component
@RequiredArgsConstructor
public class DeletePermissionHandler {
    private final PermissionRepository permissionRepository;

    @Transactional
    public void handle(Long id) {
        permissionRepository.deleteById(id);
    }
}

