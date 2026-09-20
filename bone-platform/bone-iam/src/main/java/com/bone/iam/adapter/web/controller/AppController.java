package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.adapter.web.converter.AppWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateAppReq;
import com.bone.iam.adapter.web.dto.request.GrantAppPermissionReq;
import com.bone.iam.adapter.web.dto.request.UpdateAppReq;
import com.bone.iam.application.app.command.GrantAppPermissionCommand;
import com.bone.iam.application.app.command.handler.CreateApplicationCommandHandler;
import com.bone.iam.application.app.command.handler.DeleteApplicationCommandHandler;
import com.bone.iam.application.app.command.handler.GrantAppPermissionCommandHandler;
import com.bone.iam.application.app.command.handler.RevokeAppPermissionCommandHandler;
import com.bone.iam.application.app.command.handler.UpdateApplicationCommandHandler;
import com.bone.iam.application.app.query.dto.AppPermissionDTO;
import com.bone.iam.application.app.query.dto.ApplicationDTO;
import com.bone.iam.application.app.query.handler.AppPermissionListQueryHandler;
import com.bone.iam.application.app.query.handler.ApplicationPageQueryHandler;
import com.bone.iam.application.app.query.qry.ApplicationPageQuery;
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
  private final CreateApplicationCommandHandler createApplicationCommandHandler;
  private final UpdateApplicationCommandHandler updateApplicationCommandHandler;
  private final DeleteApplicationCommandHandler deleteApplicationCommandHandler;
  private final ApplicationPageQueryHandler applicationPageQueryHandler;
  private final AppPermissionListQueryHandler appPermissionListQueryHandler;
  private final GrantAppPermissionCommandHandler grantAppPermissionCommandHandler;
  private final RevokeAppPermissionCommandHandler revokeAppPermissionCommandHandler;
  private final AppWebConverter appWebConverter;

  @GetMapping
  public ApiResponse<PageResult<ApplicationDTO>> list(ApplicationPageQuery qry) {
    return ApiResponse.success(applicationPageQueryHandler.handle(qry));
  }

  @GetMapping("/mine")
  public ApiResponse<PageResult<ApplicationDTO>> mine(ApplicationPageQuery qry) {
    return ApiResponse.success(applicationPageQueryHandler.handle(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<ApplicationDTO> detail(@PathVariable Long id) {
    return ApiResponse.success(applicationPageQueryHandler.handle(id));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateAppReq req) {
    Long id = createApplicationCommandHandler.handle(appWebConverter.toCreateCommand(req));
    return ResponseEntity.created(URI.create("/api/v1/apps/" + id)).body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateAppReq req) {
    updateApplicationCommandHandler.handle(appWebConverter.toUpdateCommand(req, id));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    deleteApplicationCommandHandler.handle(id);
    return ApiResponse.success();
  }

  @GetMapping("/{id}/permissions")
  public ApiResponse<List<AppPermissionDTO>> listPermissions(@PathVariable Long id) {
    return ApiResponse.success(appPermissionListQueryHandler.handle(id));
  }

  @PostMapping("/{id}/permissions")
  public ApiResponse<Void> grantPermission(
      @PathVariable Long id, @Valid @RequestBody GrantAppPermissionReq req) {
    GrantAppPermissionCommand cmd = new GrantAppPermissionCommand();
    cmd.setAppId(id);
    cmd.setUserId(req.getUserId());
    cmd.setRole(req.getRole());
    grantAppPermissionCommandHandler.handle(cmd);
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}/permissions/{userId}")
  public ApiResponse<Void> revokePermission(@PathVariable Long id, @PathVariable Long userId) {
    revokeAppPermissionCommandHandler.handle(id, userId);
    return ApiResponse.success();
  }
}
