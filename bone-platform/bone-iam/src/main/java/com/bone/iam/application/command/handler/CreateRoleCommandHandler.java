package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.command.cmd.CreateRoleCommand;
import com.bone.iam.application.service.TenantQuotaEnforcer;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.role.Role;
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
    timeout = 5)
@Component
@RequiredArgsConstructor
public class CreateRoleCommandHandler {
  private final RoleRepository roleRepository;
  private final TenantQuotaEnforcer tenantQuotaEnforcer;

  @Transactional
  public Long handle(CreateRoleCommand cmd) {
    // 优先使用命令中的 tenantId，其次从 TenantContext 获取，最后默认为 0L
    Long tenantId = cmd.getTenantId();
    if (tenantId == null) {
      tenantId = TenantContext.getTenantId();
    }
    if (tenantId == null) {
      tenantId = 0L;
    }
    tenantQuotaEnforcer.assertCanAddRole(tenantId);
    String code = cmd.getCode();
    if (code == null || code.isBlank()) {
      code =
          cmd.getName() == null ? "" : cmd.getName().trim().replaceAll("\\s+", "_").toUpperCase();
    }
    Role role = Role.create(cmd.getName(), code, cmd.getDescription(), 1, tenantId, null);
    roleRepository.save(role);
    return role.getId();
  }
}
