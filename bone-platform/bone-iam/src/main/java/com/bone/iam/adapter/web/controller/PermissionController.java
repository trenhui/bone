package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.PermissionWebConverter;
import com.bone.iam.adapter.web.dto.req.CreatePermissionReq;
import com.bone.iam.application.command.cmd.CreatePermissionCommand;
import com.bone.iam.application.command.cmd.UpdatePermissionCommand;
import com.bone.iam.application.command.handler.CreatePermissionCommandHandler;
import com.bone.iam.application.command.handler.DeletePermissionCommandHandler;
import com.bone.iam.application.command.handler.UpdatePermissionCommandHandler;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.handler.PermissionDetailQueryHandler;
import com.bone.iam.application.query.handler.PermissionPageQueryHandler;
import com.bone.iam.application.query.handler.PermissionTreeQueryHandler;
import com.bone.iam.application.query.qry.PermissionPageQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final CreatePermissionCommandHandler createPermissionCommandHandler;
    private final UpdatePermissionCommandHandler updatePermissionCommandHandler;
    private final DeletePermissionCommandHandler deletePermissionCommandHandler;
    private final PermissionPageQueryHandler permissionPageQueryHandler;
    private final PermissionTreeQueryHandler permissionTreeQueryHandler;
    private final PermissionDetailQueryHandler permissionDetailQueryHandler;
    private final PermissionWebConverter permissionWebConverter;

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

    @GetMapping("/tree")
    public ApiResponse<List<PermissionDTO>> tree() {
        return ApiResponse.success(permissionTreeQueryHandler.handle());
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
        return ApiResponse.success(permissionDetailQueryHandler.handle(id));
    }
}
