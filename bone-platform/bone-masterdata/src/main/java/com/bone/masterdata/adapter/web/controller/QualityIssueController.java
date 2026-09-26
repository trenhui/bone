package com.bone.masterdata.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.QualityIssueApplicationService;
import com.bone.masterdata.application.command.CreateQualityIssueCommand;
import com.bone.masterdata.domain.model.qualityissue.QualityIssue;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 质量整改工单控制器（G11 / UC-T9）。 */
@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality-issues")
@RequiredArgsConstructor
public class QualityIssueController {

  private final QualityIssueApplicationService issueService;

  @PreAuthorize("hasAuthority('masterdata:quality:write')")
  @PostMapping
  public ApiResponse<Long> create(@Valid @RequestBody CreateQualityIssueCommand cmd) {
    return ApiResponse.success(issueService.create(cmd));
  }

  @PreAuthorize("hasAuthority('masterdata:quality:write')")
  @PostMapping("/{id}/fix")
  public ApiResponse<Void> fix(@PathVariable Long id) {
    issueService.fix(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:quality:write')")
  @PostMapping("/{id}/close")
  public ApiResponse<Void> close(@PathVariable Long id) {
    issueService.close(id);
    return ApiResponse.success();
  }

  @PreAuthorize("hasAuthority('masterdata:quality:write')")
  @PostMapping("/{id}/ignore")
  public ApiResponse<Void> ignore(@PathVariable Long id) {
    issueService.ignore(id);
    return ApiResponse.success();
  }

  @GetMapping
  public ApiResponse<List<QualityIssue>> byEntity(
      @RequestParam Long masterDataEntityId, @RequestParam(required = false) String status) {
    return ApiResponse.success(issueService.byEntity(masterDataEntityId, status));
  }

  @GetMapping("/open-count")
  public ApiResponse<Long> openCount(@RequestParam Long masterDataEntityId) {
    return ApiResponse.success(issueService.openCount(masterDataEntityId));
  }
}
