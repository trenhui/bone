package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.qry.RolePageQuery;
import com.bone.iam.domain.role.Role;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.bone.iam.application.query.handler.AccountPageQueryHandler.resolveTenantFilter;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RolePageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<RoleDTO> handle(RolePageQuery qry) {
        FluentQuery<Role> query = QueryBuilder.from(Role.class);

        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            query.where(Role::getName).like(qry.getKeyword())
                       .or(Role::getDescription).like(qry.getKeyword());
        }

        // 租户隔离：见 AccountPageQueryHandler#resolveTenantFilter（详设 §3.4 / §4.8）。
        Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
        if (effectiveTenant != null) {
            query.where(Role::getTenantId).eq(effectiveTenant);
        }

        PageResult<Role> result = query.orderByDesc(Role::getCreatedAt)
                          .page(qry.getPage(), qry.getSize());

        List<RoleDTO> dtoList = result.getRecords().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private RoleDTO convertToDto(Role role) {
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setTenantId(role.getTenantId());
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        return dto;
    }
}
