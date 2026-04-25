package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.AssignPermissionCmd;
import com.bone.core.usecase.Capability;
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
public class AssignPermissionHandler {
    @Transactional
    public void handle(AssignPermissionCmd cmd) {
        // 这里需要实现角色权限分配逻辑
        // 由于使用SDK动态代理，具体实现会由SDK处理
    }
}