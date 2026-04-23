package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.qry.PermissionPageQry;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PermissionPageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<PermissionDTO> handle(PermissionPageQry qry) {
        FluentQuery<Permission> query = QueryBuilder.from(Permission.class);

        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            query.where(Permission::getName).like(qry.getKeyword())
                       .or(Permission::getCode).like(qry.getKeyword())
                       .or(Permission::getDescription).like(qry.getKeyword());
        }

        if (qry.getType() != null) {
            query.where(Permission::getType).eq(qry.getType());
        }

        if (qry.getParentId() != null) {
            query.where(Permission::getParentId).eq(String.valueOf(qry.getParentId()));
        }

        PageResult<Permission> result = query.orderByDesc(Permission::getCreateTime)
                          .page(qry.getPage(), qry.getSize());

        List<PermissionDTO> dtoList = result.getRecords().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private PermissionDTO convertToDto(Permission permission) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(permission.getDbId());
        dto.setCode(permission.getCode().value());
        dto.setName(permission.getName());
        dto.setDescription(permission.getDescription());
        dto.setParentId(permission.getParentId() != null ? Long.parseLong(permission.getParentId()) : null);
        dto.setType(permission.getType());
        dto.setCreateTime(permission.getCreateTime());
        dto.setUpdateTime(permission.getUpdateTime());
        return dto;
    }
}