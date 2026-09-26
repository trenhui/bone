package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.ModelDriftApplicationService;
import com.bone.masterdata.domain.model.drift.ModelDrift;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 模型漂移控制器（G16 / UC-T10）。targetStatus：SYNCED / IGNORED / BLOCKED。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/model-drifts")
@RequiredArgsConstructor
public class ModelDriftController {

  private final ModelDriftApplicationService driftService;

  /** 触发对账（POST /model-drifts/reconcile?masterDataEntityId=1）。 */
  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/reconcile")
  public ApiResponse<List<ModelDrift>> reconcile(@RequestParam Long masterDataEntityId) {
    return ApiResponse.success(driftService.reconcile(masterDataEntityId));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping("/{id}/handle")
  public ApiResponse<Void> handle(
      @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
    driftService.handle(id, body.get("status"));
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<ModelDrift>> byEntity(@RequestParam Long masterDataEntityId) {
    return ApiResponse.success(driftService.byEntity(masterDataEntityId));
  }
}
