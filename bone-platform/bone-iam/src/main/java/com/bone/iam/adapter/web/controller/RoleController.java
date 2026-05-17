package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.AssignPermissionCmd;
import com.bone.iam.application.command.cmd.CreateRoleCmd;
import com.bone.iam.application.command.cmd.UpdateRoleCmd;
import com.bone.iam.application.usecase.standard.CreateRoleUseCase;
import com.bone.iam.application.usecase.standard.AssignPermissionUseCase;
import com.bone.iam.application.usecase.standard.UpdateRoleUseCase;
import com.bone.iam.application.usecase.standard.DeleteRoleUseCase;
import com.bone.iam.application.usecase.standard.RolePageQueryUseCase;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.qry.RolePageQry;
import com.bone.iam.adapter.web.dto.req.CreateRoleReq;
import com.bone.iam.adapter.web.dto.resp.RoleDetailResp;
import com.bone.iam.adapter.web.converter.RoleWebConverter;
import com.bone.iam.application.query.handler.RolePermissionsQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/roles")
@RequiredArgsConstructor
public class RoleController {
    private final CreateRoleUseCase createRoleUseCase;
    private final AssignPermissionUseCase assignPermissionUseCase;
    private final UpdateRoleUseCase updateRoleUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;
    private final RolePageQueryUseCase rolePageQueryUseCase;
    private final RolePermissionsQueryHandler rolePermissionsQueryHandler;
    private final RoleWebConverter roleWebConverter;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateRoleReq req) {
        CreateRoleCmd cmd = roleWebConverter.toCreateRoleCmd(req);
        Long roleId = createRoleUseCase.execute(cmd);
        return ApiResponse.success(roleId);
    }

    @GetMapping
    public ApiResponse<PageResult<RoleDTO>> page(RolePageQry qry) {
        PageResult<RoleDTO> result = rolePageQueryUseCase.execute(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetailResp> detail(@PathVariable Long id) {
        // 实现获取角色详情逻辑
        return ApiResponse.success(new RoleDetailResp());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CreateRoleReq req) {
        UpdateRoleCmd cmd = new UpdateRoleCmd();
        cmd.setId(id);
        cmd.setName(req.getName());
        cmd.setDescription(req.getDescription());
        updateRoleUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deleteRoleUseCase.execute(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody Long[] permissionIds) {
        AssignPermissionCmd cmd = new AssignPermissionCmd();
        cmd.setRoleId(id);
        cmd.setPermissionIds(permissionIds);
        assignPermissionUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<PermissionDTO>> getPermissions(@PathVariable Long id) {
        return ApiResponse.success(rolePermissionsQueryHandler.handle(id));
    }
}