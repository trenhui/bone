package com.bone.iam.application.query.handler;

import com.bone.iam.application.query.dto.RoleDetailDTO;
import com.bone.iam.domain.role.Role;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RoleDetailQueryHandler {

    private final RolePermissionsQueryHandler rolePermissionsQueryHandler;

    @Transactional(readOnly = true)
    public Optional<RoleDetailDTO> handle(Long id) {
        return QueryBuilder.from(Role.class)
                .where(Role::getId)
                .eq(id)
                .first()
                .map(role -> {
                    RoleDetailDTO dto = new RoleDetailDTO();
                    dto.setId(role.getId());
                    dto.setName(role.getName());
                    dto.setDescription(role.getDescription());
                    dto.setTenantId(role.getTenantId());
                    dto.setCreatedAt(role.getCreatedAt());
                    dto.setUpdatedAt(role.getUpdatedAt());
                    List<Long> permissionIds = rolePermissionsQueryHandler.handle(id).stream()
                            .map(p -> p.getId())
                            .toList();
                    dto.setPermissionIds(permissionIds.toArray(Long[]::new));
                    return dto;
                });
    }
}
