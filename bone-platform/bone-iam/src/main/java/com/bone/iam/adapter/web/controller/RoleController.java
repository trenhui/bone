package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.AssignPermissionCommand;
import com.bone.iam.application.command.cmd.CreateRoleCommand;
import com.bone.iam.application.command.cmd.UpdateRoleCommand;
import com.bone.iam.application.command.handler.CreateRoleCommandHandler;
import com.bone.iam.application.command.handler.AssignPermissionCommandHandler;
import com.bone.iam.application.command.handler.UpdateRoleCommandHandler;
import com.bone.iam.application.command.handler.DeleteRoleCommandHandler;
import com.bone.iam.application.query.handler.RolePageQueryHandler;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.qry.RolePageQuery;
import com.bone.iam.adapter.web.dto.req.CreateRoleReq;
import com.bone.iam.adapter.web.dto.resp.RoleDetailResp;
import com.bone.iam.adapter.web.converter.RoleWebConverter;
import com.bone.iam.application.query.handler.RoleDetailQueryHandler;
import com.bone.iam.application.query.handler.RolePermissionsQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/roles")
@RequiredArgsConstructor
public class RoleController {
    private final CreateRoleCommandHandler createRoleCommandHandler;
    private final AssignPermissionCommandHandler assignPermissionCommandHandler;
    private final UpdateRoleCommandHandler updateRoleCommandHandler;
    private final DeleteRoleCommandHandler deleteRoleCommandHandler;
    private final RolePageQueryHandler rolePageQueryHandler;
    private final RolePermissionsQueryHandler rolePermissionsQueryHandler;
    private final RoleDetailQueryHandler roleDetailQueryHandler;
    private final RoleWebConverter roleWebConverter;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateRoleReq req) {
        CreateRoleCommand cmd = roleWebConverter.toCreateRoleCommand(req);
        Long roleId = createRoleCommandHandler.handle(cmd);
        return ApiResponse.success(roleId);
    }

    @GetMapping
    public ApiResponse<PageResult<RoleDTO>> page(RolePageQuery qry) {
        PageResult<RoleDTO> result = rolePageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetailResp> detail(@PathVariable Long id) {
        RoleDetailResp resp = roleDetailQueryHandler
                .handle(id)
                .map(roleWebConverter::toDetailResp)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        return ApiResponse.success(resp);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CreateRoleReq req) {
        UpdateRoleCommand cmd = new UpdateRoleCommand();
        cmd.setId(id);
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        updateRoleCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deleteRoleCommandHandler.handle(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody Long[] permissionIds) {
        AssignPermissionCommand cmd = new AssignPermissionCommand();
        cmd.setRoleId(id);
        cmd.setPermissionIds(permissionIds);
        assignPermissionCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<PermissionDTO>> getPermissions(@PathVariable Long id) {
        return ApiResponse.success(rolePermissionsQueryHandler.handle(id));
    }
}