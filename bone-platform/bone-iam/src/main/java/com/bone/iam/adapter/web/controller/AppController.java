package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.adapter.web.converter.AppWebConverter;
import com.bone.iam.adapter.web.dto.req.CreateAppReq;
import com.bone.iam.adapter.web.dto.req.UpdateAppReq;
import com.bone.iam.application.app.command.handler.CreateApplicationCommandHandler;
import com.bone.iam.application.app.command.handler.DeleteApplicationCommandHandler;
import com.bone.iam.application.app.command.handler.UpdateApplicationCommandHandler;
import com.bone.iam.application.app.query.dto.ApplicationDTO;
import com.bone.iam.application.app.query.handler.ApplicationPageQueryHandler;
import com.bone.iam.application.app.query.qry.ApplicationPageQuery;
import jakarta.validation.Valid;
import java.net.URI;
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
}
