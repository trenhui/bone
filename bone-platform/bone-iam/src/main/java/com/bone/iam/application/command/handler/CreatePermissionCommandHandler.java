package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.permission.vo.PermissionType;
import com.bone.iam.domain.repository.PermissionRepository;
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
    timeout = 5)
@Component
@RequiredArgsConstructor
public class CreatePermissionCommandHandler {
  private final PermissionRepository permissionRepository;

  @Transactional
  public Long handle(CreatePermissionCommand cmd) {
    // resourceType 为空时设置默认值
    String resourceType = cmd.getResourceType();
    if (resourceType == null || resourceType.isBlank()) {
      resourceType = "API";
    }
    // resourcePath 为空时设置默认值
    String resourcePath = cmd.getResourcePath();
    if (resourcePath == null || resourcePath.isBlank()) {
      resourcePath = cmd.getCode() != null ? "/" + cmd.getCode() : "/";
    }
    // action 为空时设置默认值
    String action = cmd.getAction();
    if (action == null || action.isBlank()) {
      action = "ALL";
    }
    // type 为空时设置默认值
    PermissionType type = cmd.getType();
    if (type == null) {
      type = PermissionType.OPERATION;
    }
    Permission permission =
        Permission.create(
            cmd.getCode(),
            cmd.getName(),
            cmd.getDescription(),
            resourceType,
            resourcePath,
            action,
            cmd.getParentId(),
            type,
            cmd.getSortOrder());
    permissionRepository.save(permission);
    return permission.getId();
  }
}
