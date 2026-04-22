package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.AssignPermissionCmd;
import com.bone.iam.application.command.cmd.CreateRoleCmd;
import com.bone.iam.application.command.handler.AssignPermissionHandler;
import com.bone.iam.application.command.handler.CreateRoleHandler;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.handler.RolePageQueryHandler;
import com.bone.iam.application.query.qry.RolePageQry;
import com.bone.iam.adapter.web.dto.req.CreateRoleReq;
import com.bone.iam.adapter.web.dto.resp.RoleDetailResp;
import com.bone.iam.adapter.web.converter.RoleWebConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/roles")
@RequiredArgsConstructor
public class RoleController {
    private final CreateRoleHandler createRoleHandler;
    private final AssignPermissionHandler assignPermissionHandler;
    private final RolePageQueryHandler rolePageQueryHandler;
    private final RoleWebConverter roleWebConverter;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateRoleReq req) {
        CreateRoleCmd cmd = roleWebConverter.toCreateRoleCmd(req);
        Long roleId = createRoleHandler.handle(cmd);
        return ApiResponse.success(roleId);
    }

    @GetMapping
    public ApiResponse<PageResult<RoleDTO>> page(RolePageQry qry) {
        PageResult<RoleDTO> result = rolePageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetailResp> detail(@PathVariable Long id) {
        // 实现获取角色详情逻辑
        return ApiResponse.success(new RoleDetailResp());
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody Long[] permissionIds) {
        AssignPermissionCmd cmd = new AssignPermissionCmd();
        cmd.setRoleId(id);
        cmd.setPermissionIds(permissionIds);
        assignPermissionHandler.handle(cmd);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<?> getPermissions(@PathVariable Long id) {
        // 实现获取角色权限逻辑
        return ApiResponse.success();
    }
}