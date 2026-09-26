package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.TenantWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateTenantReq;
import com.bone.iam.adapter.web.dto.request.UpdateTenantQuotaReq;
import com.bone.iam.adapter.web.dto.request.UpdateTenantReq;
import com.bone.iam.adapter.web.dto.response.CreateTenantResp;
import com.bone.iam.application.TenantApplicationService;
import com.bone.iam.application.query.dto.TenantDTO;
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

  private final TenantApplicationService tenantApplicationService;
  private final TenantWebConverter tenantWebConverter;

  @GetMapping
  @PreAuthorize("hasAuthority('iam:tenants:read') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<PageResult<TenantDTO>> list(TenantPageQuery qry) {
    return ApiResponse.success(tenantApplicationService.page(qry));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('iam:tenants:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<CreateTenantResp> create(@RequestBody CreateTenantReq req) {
    TenantApplicationService.CreateTenantResult result =
        tenantApplicationService.create(tenantWebConverter.toCreateTenantCommand(req));
    CreateTenantResp resp = new CreateTenantResp();
    resp.setTenantId(result.tenantId());
    resp.setAdminAccountId(result.adminAccountId());
    resp.setAdminUsername(result.adminUsername());
    resp.setInitialPassword(result.initialPassword());
    return ApiResponse.success(resp);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:tenants:read') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<TenantDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(tenantApplicationService.detail(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:tenants:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateTenantReq req) {
    tenantApplicationService.update(tenantWebConverter.toUpdateTenantCommand(id, req));
    return ApiResponse.success();
  }

  @PostMapping("/{id}/enable")
  @PreAuthorize("hasAuthority('iam:tenants:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> enable(@PathVariable Long id) {
    tenantApplicationService.enable(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/disable")
  @PreAuthorize("hasAuthority('iam:tenants:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> disable(@PathVariable Long id) {
    tenantApplicationService.disable(id);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:tenants:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    tenantApplicationService.delete(id);
    return ApiResponse.success();
  }

  @PutMapping("/{id}/quota")
  @PreAuthorize("hasAuthority('iam:tenants:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> updateQuota(
      @PathVariable Long id, @RequestBody UpdateTenantQuotaReq req) {
    tenantApplicationService.updateQuota(tenantWebConverter.toUpdateTenantQuotaCommand(id, req));
    return ApiResponse.success();
  }
}
