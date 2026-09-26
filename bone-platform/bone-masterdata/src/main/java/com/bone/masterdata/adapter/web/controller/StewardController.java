package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.StewardApplicationService;
import com.bone.masterdata.application.command.AssignGovernanceRoleCommand;
import com.bone.masterdata.domain.model.steward.StewardAssignment;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 治理角色控制器（G3 / UC-T2）。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/governance-roles")
@RequiredArgsConstructor
public class StewardController {

  private final StewardApplicationService stewardService;

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @PostMapping
  public ApiResponse<Long> assign(@Valid @RequestBody AssignGovernanceRoleCommand cmd) {
    return ApiResponse.success(stewardService.assign(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:governance:write')")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> unassign(@PathVariable Long id) {
    stewardService.unassign(id);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<StewardAssignment>> byEntity(@RequestParam Long masterDataEntityId) {
    return ApiResponse.success(stewardService.byEntity(masterDataEntityId));
  }
}
