package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.TenantWebConverter;
import com.bone.iam.adapter.web.dto.req.CreateTenantReq;
import com.bone.iam.adapter.web.dto.req.UpdateTenantQuotaReq;
import com.bone.iam.adapter.web.dto.req.UpdateTenantReq;
import com.bone.iam.application.command.handler.CreateTenantCommandHandler;
import com.bone.iam.application.command.handler.DeleteTenantCommandHandler;
import com.bone.iam.application.command.handler.DisableTenantCommandHandler;
import com.bone.iam.application.command.handler.EnableTenantCommandHandler;
import com.bone.iam.application.command.handler.UpdateTenantCommandHandler;
import com.bone.iam.application.command.handler.UpdateTenantQuotaCommandHandler;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.application.query.handler.TenantDetailQueryHandler;
import com.bone.iam.application.query.handler.TenantPageQueryHandler;
import com.bone.iam.application.query.qry.TenantPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
  private final DeleteTenantCommandHandler deleteTenantCommandHandler;
  private final UpdateTenantQuotaCommandHandler updateTenantQuotaCommandHandler;
  private final TenantWebConverter tenantWebConverter;

  @GetMapping
  @PreAuthorize("hasAuthority('iam:tenants:read')")
  public ApiResponse<PageResult<TenantDTO>> list(TenantPageQuery qry) {
    return ApiResponse.success(tenantPageQueryHandler.handle(qry));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('iam:tenants:write')")
  public ApiResponse<Long> create(@RequestBody CreateTenantReq req) {
    return ApiResponse.success(
        createTenantCommandHandler.handle(tenantWebConverter.toCreateTenantCommand(req)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:tenants:read')")
  public ApiResponse<TenantDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(tenantDetailQueryHandler.handle(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:tenants:write')")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateTenantReq req) {
    updateTenantCommandHandler.handle(tenantWebConverter.toUpdateTenantCommand(id, req));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/enable")
  @PreAuthorize("hasAuthority('iam:tenants:write')")
  public ApiResponse<Void> enable(@PathVariable Long id) {
    enableTenantCommandHandler.handle(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/disable")
  @PreAuthorize("hasAuthority('iam:tenants:write')")
  public ApiResponse<Void> disable(@PathVariable Long id) {
    disableTenantCommandHandler.handle(id);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:tenants:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteTenantCommandHandler.handle(id);
    return ApiResponse.success();
  }

  @PutMapping("/{id}/quota")
  @PreAuthorize("hasAuthority('iam:tenants:write')")
  public ApiResponse<Void> updateQuota(
      @PathVariable Long id, @RequestBody UpdateTenantQuotaReq req) {
    updateTenantQuotaCommandHandler.handle(tenantWebConverter.toUpdateTenantQuotaCommand(id, req));
    return ApiResponse.success();
  }
}
