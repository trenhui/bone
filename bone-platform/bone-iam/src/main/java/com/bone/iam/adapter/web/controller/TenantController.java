package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.application.command.cmd.CreateTenantCommand;
import com.bone.iam.application.command.cmd.UpdateTenantCommand;
import com.bone.iam.application.command.handler.CreateTenantHandler;
import com.bone.iam.application.command.handler.DisableTenantHandler;
import com.bone.iam.application.command.handler.EnableTenantHandler;
import com.bone.iam.application.command.handler.UpdateTenantHandler;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.application.query.handler.TenantPageQueryHandler;
import com.bone.iam.application.query.qry.TenantPageQuery;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final CreateTenantHandler createTenantHandler;
    private final UpdateTenantHandler updateTenantHandler;
    private final EnableTenantHandler enableTenantHandler;
    private final DisableTenantHandler disableTenantHandler;
    private final TenantPageQueryHandler tenantPageQueryHandler;
    private final TenantRepository tenantRepository;

    @GetMapping
    public ApiResponse<PageResult<TenantDTO>> list(TenantPageQuery qry) {
        return ApiResponse.success(tenantPageQueryHandler.handle(qry));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateTenantCommand cmd) {
        return ApiResponse.success(createTenantHandler.handle(cmd));
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantDTO> detail(@PathVariable Long id) {
        Tenant tenant = tenantRepository.findById(id);
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "租户不存在");
        }
        TenantDTO dto = new TenantDTO();
        dto.setId(tenant.getId());
        dto.setName(tenant.getName());
        dto.setCode(tenant.getCode());
        dto.setLevel(tenant.getLevel());
        dto.setStatus(tenant.getStatus());
        dto.setAdminEmail(tenant.getAdminEmail());
        return ApiResponse.success(dto);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateTenantCommand cmd) {
        cmd.setId(id);
        updateTenantHandler.handle(cmd);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        enableTenantHandler.handle(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        disableTenantHandler.handle(id);
        return ApiResponse.success();
    }
}
