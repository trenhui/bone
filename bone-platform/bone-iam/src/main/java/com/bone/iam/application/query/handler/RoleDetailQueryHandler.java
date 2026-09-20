package com.bone.iam.application.query.handler;

import com.bone.iam.application.query.dto.RoleDetailDTO;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoleDetailQueryHandler {

  private final RoleRepository roleRepository;
  private final RolePermissionsQueryHandler rolePermissionsQueryHandler;
  private final TenantProvider tenantProvider;

  @Transactional(readOnly = true)
  public Optional<RoleDetailDTO> handle(Long id) {
    return Optional.ofNullable(roleRepository.findById(id))
        // 租户隔离：非平台租户不可查看其他租户的角色详情（防 IDOR）。详设 §3.4 / §4.8。
        .filter(
            role -> {
              Long caller = tenantProvider.currentTenantIdOrNull();
              return caller == null || caller == 0L || caller.equals(role.getTenantId());
            })
        .map(
            role -> {
              RoleDetailDTO dto = new RoleDetailDTO();
              dto.setId(role.getId());
              dto.setName(role.getName());
              dto.setDescription(role.getDescription());
              dto.setTenantId(role.getTenantId());
              dto.setCreatedAt(role.getCreatedAt());
              dto.setUpdatedAt(role.getUpdatedAt());
              List<Long> permissionIds =
                  rolePermissionsQueryHandler.handle(id).stream().map(p -> p.getId()).toList();
              dto.setPermissionIds(permissionIds.toArray(Long[]::new));
              return dto;
            });
  }
}
