package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import com.bone.iam.application.command.cmd.UpdatePermissionCommand;
import com.bone.iam.application.command.handler.CreatePermissionCommandHandler;
import com.bone.iam.application.command.handler.UpdatePermissionCommandHandler;
import com.bone.iam.application.command.handler.DeletePermissionCommandHandler;
import com.bone.iam.application.query.handler.PermissionPageQueryHandler;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.qry.PermissionPageQuery;
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
@RequestMapping(PlatformApiPaths.IAM_V1 + "/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final CreatePermissionCommandHandler createPermissionCommandHandler;
    private final UpdatePermissionCommandHandler updatePermissionCommandHandler;
    private final DeletePermissionCommandHandler deletePermissionCommandHandler;
    private final PermissionPageQueryHandler permissionPageQueryHandler;
    private final PermissionWebConverter permissionWebConverter;
    private final PermissionRepository permissionRepository;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreatePermissionReq req) {
        CreatePermissionCommand cmd = permissionWebConverter.toCreatePermissionCommand(req);
        Long permissionId = createPermissionCommandHandler.handle(cmd);
        return ApiResponse.success(permissionId);
    }

    @GetMapping
    public ApiResponse<PageResult<PermissionDTO>> page(PermissionPageQuery qry) {
        PageResult<PermissionDTO> result = permissionPageQueryHandler.handle(qry);
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
        UpdatePermissionCommand cmd = new UpdatePermissionCommand();
        cmd.setId(id);
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        cmd.setResourceType(req.getResourceType());
        cmd.setResourcePath(req.getResourcePath());
        cmd.setAction(req.getAction());
        cmd.setParentId(req.getParentId());
        cmd.setType(req.getType());
        cmd.setSortOrder(req.getSortOrder());
        updatePermissionCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deletePermissionCommandHandler.handle(id);
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