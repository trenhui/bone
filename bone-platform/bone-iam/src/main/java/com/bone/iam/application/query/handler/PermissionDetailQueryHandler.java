package com.bone.iam.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.domain.permission.Permission;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PermissionDetailQueryHandler {

  @Transactional(readOnly = true)
  public PermissionDTO handle(Long id) {
    Permission permission =
        QueryBuilder.from(Permission.class)
            .where(Permission::getId)
            .eq(id)
            .first()
            .orElseThrow(() -> NotFoundException.of("权限不存在"));
    PermissionDTO dto = new PermissionDTO();
    dto.setId(permission.getId());
    dto.setCode(permission.getCode());
    dto.setName(permission.getName());
    dto.setResourceType(permission.getResourceType());
    dto.setResourcePath(permission.getResourcePath());
    dto.setAction(permission.getAction());
    return dto;
  }
}
