package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.application.command.cmd.CreateTenantCommand;
import com.bone.iam.application.command.cmd.UpdateTenantCommand;
import com.bone.iam.application.command.handler.CreateTenantCommandHandler;
import com.bone.iam.application.command.handler.DisableTenantCommandHandler;
import com.bone.iam.application.command.handler.EnableTenantCommandHandler;
import com.bone.iam.application.command.handler.UpdateTenantCommandHandler;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.application.query.handler.TenantDetailQueryHandler;
import com.bone.iam.application.query.handler.TenantPageQueryHandler;
import com.bone.iam.application.query.qry.TenantPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final CreateTenantCommandHandler createTenantCommandHandler;
    private final UpdateTenantCommandHandler updateTenantCommandHandler;
    private final EnableTenantCommandHandler enableTenantCommandHandler;
    private final DisableTenantCommandHandler disableTenantCommandHandler;
    private final TenantPageQueryHandler tenantPageQueryHandler;
    private final TenantDetailQueryHandler tenantDetailQueryHandler;

    @GetMapping
    public ApiResponse<PageResult<TenantDTO>> list(TenantPageQuery qry) {
        return ApiResponse.success(tenantPageQueryHandler.handle(qry));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateTenantCommand cmd) {
        return ApiResponse.success(createTenantCommandHandler.handle(cmd));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantDTO> detail(@PathVariable Long id) {
        return ApiResponse.success(tenantDetailQueryHandler.handle(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateTenantCommand cmd) {
        cmd.setId(id);
        updateTenantCommandHandler.handle(cmd);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        enableTenantCommandHandler.handle(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        disableTenantCommandHandler.handle(id);
        return ApiResponse.success();
    }
}
