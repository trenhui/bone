package com.bone.iam.application.query.handler;

import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.role.RolePermission;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RolePermissionsQueryHandler {

  @Transactional(readOnly = true)
  public List<PermissionDTO> handle(Long roleId) {
    if (roleId == null) {
      return Collections.emptyList();
    }
    List<RolePermission> links =
        QueryBuilder.from(RolePermission.class).where(RolePermission::getRoleId).eq(roleId).list();
    if (links.isEmpty()) {
      return Collections.emptyList();
    }
    List<Long> permissionIds =
        links.stream().map(RolePermission::getPermissionId).distinct().toList();
    List<Permission> permissions =
        QueryBuilder.from(Permission.class).where(Permission::getId).in(permissionIds).list();
    return permissions.stream().map(this::toDto).collect(Collectors.toList());
  }

  private PermissionDTO toDto(Permission p) {
    PermissionDTO dto = new PermissionDTO();
    dto.setId(p.getId());
    dto.setCode(p.getCode());
    dto.setName(p.getName());
    dto.setResourceType(p.getResourceType());
    dto.setResourcePath(p.getResourcePath());
    dto.setAction(p.getAction());
    return dto;
  }
}
