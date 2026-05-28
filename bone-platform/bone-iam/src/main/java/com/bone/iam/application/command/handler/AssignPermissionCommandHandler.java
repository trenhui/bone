package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.command.cmd.AssignPermissionCommand;
import com.bone.iam.application.service.RolePermissionBindingService;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.role.Role;
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
        Role role = roleRepository.findById(cmd.getRoleId());
        if (role == null) {
            throw new RuntimeException("角色不存在: " + cmd.getRoleId());
        }
        assertCallerMayManageRole(role);
        rolePermissionBindingService.replaceBindings(cmd.getRoleId(), cmd.getPermissionIds());
    }

    /** 非平台租户（tenantId &gt; 0）只能操作本租户角色，防 IDOR。 */
    static void assertCallerMayManageRole(Role role) {
        Long callerTenant = TenantContext.getTenantId();
        if (callerTenant != null && callerTenant != 0L && !callerTenant.equals(role.getTenantId())) {
            throw BizException.of(
                    403, IamErrorCodes.TENANT_ACCESS_DENIED + ": 无权操作其他租户的角色");
        }
    }
}
