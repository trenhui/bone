package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.CreatePermissionCmd;
import com.bone.iam.application.command.handler.CreatePermissionHandler;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.handler.PermissionPageQueryHandler;
import com.bone.iam.application.query.qry.PermissionPageQry;
import com.bone.iam.adapter.web.dto.req.CreatePermissionReq;
import com.bone.iam.adapter.web.converter.PermissionWebConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final CreatePermissionHandler createPermissionHandler;
    private final PermissionPageQueryHandler permissionPageQueryHandler;
    private final PermissionWebConverter permissionWebConverter;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreatePermissionReq req) {
        CreatePermissionCmd cmd = permissionWebConverter.toCreatePermissionCmd(req);
        Long permissionId = createPermissionHandler.handle(cmd);
        return ApiResponse.success(permissionId);
    }

    @GetMapping
    public ApiResponse<PageResult<PermissionDTO>> page(PermissionPageQry qry) {
        PageResult<PermissionDTO> result = permissionPageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/tree")
    public ApiResponse<?> tree() {
        // 实现获取权限树逻辑
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<PermissionDTO> detail(@PathVariable Long id) {
        // 实现获取权限详情逻辑
        return ApiResponse.success(new PermissionDTO());
    }
}