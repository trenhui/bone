package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.adapter.web.converter.AppWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateAppReq;
import com.bone.iam.adapter.web.dto.request.GrantAppPermissionReq;
import com.bone.iam.adapter.web.dto.request.UpdateAppReq;
import com.bone.iam.application.AppApplicationService;
import com.bone.iam.application.command.GrantAppPermissionCommand;
import com.bone.iam.application.query.dto.AppPermissionDTO;
import com.bone.iam.application.query.dto.ApplicationDTO;
import com.bone.iam.application.query.qry.ApplicationPageQuery;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/apps")
@RequiredArgsConstructor
public class AppController {
  private final AppApplicationService appApplicationService;
  private final AppWebConverter appWebConverter;

  @GetMapping
  public ApiResponse<PageResult<ApplicationDTO>> list(ApplicationPageQuery qry) {
    return ApiResponse.success(appApplicationService.pageApplications(qry));
  }

  @GetMapping("/mine")
  public ApiResponse<PageResult<ApplicationDTO>> mine(ApplicationPageQuery qry) {
    return ApiResponse.success(appApplicationService.pageApplications(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<ApplicationDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(appApplicationService.applicationDetail(id));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateAppReq req) {
    Long id = appApplicationService.createApplication(appWebConverter.toCreateCommand(req));
    return ResponseEntity.created(URI.create("/api/v1/apps/" + id)).body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateAppReq req) {
    appApplicationService.updateApplication(appWebConverter.toUpdateCommand(req, id));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    appApplicationService.deleteApplication(id);
    return ApiResponse.success();
  }

  @GetMapping("/{id}/permissions")
  public ApiResponse<List<AppPermissionDTO>> listPermissions(@PathVariable Long id) {
    return ApiResponse.success(appApplicationService.permissions(id));
  }

  @PostMapping("/{id}/permissions")
  public ApiResponse<Void> grantPermission(
      @PathVariable Long id, @Valid @RequestBody GrantAppPermissionReq req) {
    GrantAppPermissionCommand cmd = new GrantAppPermissionCommand();
    cmd.setAppId(id);
    cmd.setUserId(req.getUserId());
    cmd.setRole(req.getRole());
    appApplicationService.grantPermission(cmd);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}/permissions/{userId}")
  public ApiResponse<Void> revokePermission(@PathVariable Long id, @PathVariable Long userId) {
    appApplicationService.revokePermission(id, userId);
    return ApiResponse.success();
  }
}
