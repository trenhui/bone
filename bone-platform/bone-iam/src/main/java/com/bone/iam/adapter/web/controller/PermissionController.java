package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.PermissionWebConverter;
import com.bone.iam.adapter.web.dto.request.CreatePermissionReq;
import com.bone.iam.application.PermissionApplicationService;
import com.bone.iam.application.command.CreatePermissionCommand;
import com.bone.iam.application.command.UpdatePermissionCommand;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.qry.PermissionPageQuery;
import java.util.List;
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
@RequestMapping(PlatformApiPaths.IAM_V1 + "/permissions")
@RequiredArgsConstructor
public class PermissionController {
  private final PermissionApplicationService permissionApplicationService;
  private final PermissionWebConverter permissionWebConverter;

  @PostMapping
  @PreAuthorize("hasAuthority('iam:permissions:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Long> create(@RequestBody CreatePermissionReq req) {
    CreatePermissionCommand cmd = permissionWebConverter.toCreatePermissionCommand(req);
    Long permissionId = permissionApplicationService.create(cmd);
    return ApiResponse.success(permissionId);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('iam:permissions:read') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<PageResult<PermissionDTO>> page(PermissionPageQuery qry) {
    PageResult<PermissionDTO> result = permissionApplicationService.page(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/tree")
  @PreAuthorize("hasAuthority('iam:permissions:read') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<List<PermissionDTO>> tree() {
    return ApiResponse.success(permissionApplicationService.tree());
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:permissions:write') and @platformAccessGuard.isPlatformAdmin()")
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
    permissionApplicationService.update(cmd);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:permissions:write') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    permissionApplicationService.delete(id);
    return ApiResponse.success();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:permissions:read') and @platformAccessGuard.isPlatformAdmin()")
  public ApiResponse<PermissionDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(permissionApplicationService.detail(id));
  }
}
