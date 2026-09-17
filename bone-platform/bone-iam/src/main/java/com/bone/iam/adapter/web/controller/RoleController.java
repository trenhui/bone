package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.RoleWebConverter;
import com.bone.iam.adapter.web.dto.request.AssignPermissionReq;
import com.bone.iam.adapter.web.dto.request.CreateRoleReq;
import com.bone.iam.adapter.web.dto.request.UpdateRoleReq;
import com.bone.iam.adapter.web.dto.response.RoleDetailResp;
import com.bone.iam.application.command.cmd.CreateRoleCommand;
import com.bone.iam.application.command.handler.AssignPermissionCommandHandler;
import com.bone.iam.application.command.handler.CreateRoleCommandHandler;
import com.bone.iam.application.command.handler.DeleteRoleCommandHandler;
import com.bone.iam.application.command.handler.UpdateRoleCommandHandler;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.handler.RoleDetailQueryHandler;
import com.bone.iam.application.query.handler.RolePageQueryHandler;
import com.bone.iam.application.query.handler.RolePermissionsQueryHandler;
import com.bone.iam.application.query.qry.RolePageQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
  @PreAuthorize("hasAuthority('iam:roles:write')")
  public ApiResponse<Long> create(@RequestBody CreateRoleReq req) {
    CreateRoleCommand cmd = roleWebConverter.toCreateRoleCommand(req);
    Long roleId = createRoleCommandHandler.handle(cmd);
    return ApiResponse.success(roleId);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('iam:roles:read')")
  public ApiResponse<PageResult<RoleDTO>> page(RolePageQuery qry) {
    PageResult<RoleDTO> result = rolePageQueryHandler.handle(qry);
    return ApiResponse.success(result);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:roles:read')")
  public ApiResponse<RoleDetailResp> detail(@PathVariable Long id) {
    RoleDetailResp resp =
        roleDetailQueryHandler
            .handle(id)
            .map(roleWebConverter::toDetailResp)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
    return ApiResponse.success(resp);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:roles:write')")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateRoleReq req) {
    updateRoleCommandHandler.handle(roleWebConverter.toUpdateRoleCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:roles:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteRoleCommandHandler.handle(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('iam:roles:write')")
  public ApiResponse<Void> assignPermissions(
      @PathVariable Long id, @RequestBody AssignPermissionReq req) {
    assignPermissionCommandHandler.handle(roleWebConverter.toAssignPermissionCommand(id, req));
    return ApiResponse.success();
  }

  @GetMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('iam:roles:read')")
  public ApiResponse<List<PermissionDTO>> getPermissions(@PathVariable Long id) {
    return ApiResponse.success(rolePermissionsQueryHandler.handle(id));
  }
}
