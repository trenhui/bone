package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.CreatePermissionCmd;
import com.bone.iam.application.command.cmd.UpdatePermissionCmd;
import com.bone.iam.application.usecase.standard.CreatePermissionUseCase;
import com.bone.iam.application.usecase.standard.UpdatePermissionUseCase;
import com.bone.iam.application.usecase.standard.DeletePermissionUseCase;
import com.bone.iam.application.usecase.standard.PermissionPageQueryUseCase;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.qry.PermissionPageQry;
import com.bone.iam.adapter.web.dto.req.CreatePermissionReq;
import com.bone.iam.adapter.web.converter.PermissionWebConverter;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/iam/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final CreatePermissionUseCase createPermissionUseCase;
    private final UpdatePermissionUseCase updatePermissionUseCase;
    private final DeletePermissionUseCase deletePermissionUseCase;
    private final PermissionPageQueryUseCase permissionPageQueryUseCase;
    private final PermissionWebConverter permissionWebConverter;
    private final PermissionRepository permissionRepository;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreatePermissionReq req) {
        CreatePermissionCmd cmd = permissionWebConverter.toCreatePermissionCmd(req);
        Long permissionId = createPermissionUseCase.execute(cmd);
        return ApiResponse.success(permissionId);
    }

    @GetMapping
    public ApiResponse<PageResult<PermissionDTO>> page(PermissionPageQry qry) {
        PageResult<PermissionDTO> result = permissionPageQueryUseCase.execute(qry);
        return ApiResponse.success(result);
    }

    /**
     * 获取权限树
     * 按层级结构返回所有权限
     */
    @GetMapping("/tree")
    public ApiResponse<List<PermissionDTO>> tree() {
        List<Permission> allPermissions = QueryBuilder.from(Permission.class).list();

        // 转换为DTO
        List<PermissionDTO> dtoList = allPermissions.stream()
                .map(p -> {
                    PermissionDTO dto = new PermissionDTO();
                    dto.setId(p.getId());
                    dto.setCode(p.getCode());
                    dto.setName(p.getName());
                    dto.setResourceType(p.getResourceType());
                    dto.setResourcePath(p.getResourcePath());
                    dto.setAction(p.getAction());
                    dto.setParentId(p.getParentId());
                    dto.setSortOrder(p.getSortOrder());
                    dto.setChildren(new ArrayList<>());
                    return dto;
                })
                .toList();

        // 构建树结构
        Map<Long, List<PermissionDTO>> childrenMap = dtoList.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(PermissionDTO::getParentId));

        // 设置子节点
        dtoList.forEach(d -> d.setChildren(childrenMap.getOrDefault(d.getId(), new ArrayList<>())));

        // 返回顶层节点
        List<PermissionDTO> tree = dtoList.stream()
                .filter(d -> d.getParentId() == null)
                .sorted((a, b) -> Integer.compare(a.getSortOrder() == null ? 0 : a.getSortOrder(),
                        b.getSortOrder() == null ? 0 : b.getSortOrder()))
                .toList();

        return ApiResponse.success(tree);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CreatePermissionReq req) {
        UpdatePermissionCmd cmd = new UpdatePermissionCmd();
        cmd.setId(id);
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        cmd.setResourceType(req.getResourceType());
        cmd.setResourcePath(req.getResourcePath());
        cmd.setAction(req.getAction());
        cmd.setParentId(req.getParentId());
        cmd.setType(req.getType());
        cmd.setSortOrder(req.getSortOrder());
        updatePermissionUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deletePermissionUseCase.execute(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<PermissionDTO> detail(@PathVariable Long id) {
        Permission permission = QueryBuilder.from(Permission.class)
                .where(Permission::getId).eq(id)
                .first()
                .orElseThrow(() -> new IllegalArgumentException("权限不存在"));
        PermissionDTO dto = new PermissionDTO();
        dto.setId(permission.getId());
        dto.setCode(permission.getCode());
        dto.setName(permission.getName());
        dto.setResourceType(permission.getResourceType());
        dto.setResourcePath(permission.getResourcePath());
        dto.setAction(permission.getAction());
        return ApiResponse.success(dto);
    }
}