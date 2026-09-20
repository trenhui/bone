package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.adapter.web.converter.ModuleWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateModuleReq;
import com.bone.iam.adapter.web.dto.request.UpdateModuleReq;
import com.bone.iam.application.ModuleApplicationService;
import com.bone.iam.application.query.dto.ModuleDTO;
import com.bone.iam.application.query.qry.ModuleListQuery;
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
@RequestMapping("/api/v1/apps/{appId}/modules")
@RequiredArgsConstructor
public class ModuleController {
  private final ModuleApplicationService moduleApplicationService;
  private final ModuleWebConverter moduleWebConverter;

  @GetMapping
  public ApiResponse<PageResult<ModuleDTO>> list(@PathVariable Long appId, ModuleListQuery qry) {
    qry.setAppId(appId);
    return ApiResponse.success(moduleApplicationService.listModules(qry));
  }

  @GetMapping("/{id}")
  public ApiResponse<ModuleDTO> detail(@PathVariable Long appId, @PathVariable Long id) {
    return ApiResponse.success(moduleApplicationService.moduleDetail(id));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Long>> create(
      @PathVariable Long appId, @Valid @RequestBody CreateModuleReq req) {
    Long id = moduleApplicationService.createModule(moduleWebConverter.toCreateCommand(req, appId));
    return ResponseEntity.created(URI.create("/api/v1/apps/" + appId + "/modules/" + id))
        .body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateModuleReq req) {
    moduleApplicationService.updateModule(moduleWebConverter.toUpdateCommand(req, id));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    moduleApplicationService.deleteModule(id);
    return ApiResponse.success();
  }
}
