package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.CoverageOverrideApplicationService;
import com.bone.claim.application.CoverageOverrideApplicationService.AffectedPoliciesView;
import com.bone.claim.application.CoverageOverrideApplicationService.CreateOverrideCommand;
import com.bone.claim.application.CoverageOverrideApplicationService.MergePreviewRow;
import com.bone.claim.application.EffectiveConfigViewService.EffectiveConfig;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 两段式覆盖 REST：标准配置不动，覆盖项 DISABLED→ACTIVE 两段生效。
 *
 * <p>发布流：{@code GET /affected-count}（前端确认页展示受影响保单数）→ {@code POST /publish}。 预览与生效查询只读，不产生任何状态变更。
 */
@Tag(name = "ClaimCoverageOverride", description = "责任配置保司覆盖（两段式）")
@RestController
@RequestMapping("/api/v1/claim-config/coverage-overrides")
@RequiredArgsConstructor
public class CoverageOverrideController {

  private final CoverageOverrideApplicationService overrideService;
  private final com.bone.claim.application.EffectiveConfigViewService effectiveConfigViewService;

  @Operation(summary = "创建/更新覆盖项（同维度唯一；已发布行须先停用）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping
  public ApiResponse<MergePreviewRow> upsert(@RequestBody UpsertReq req) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        overrideService.upsert(
            new CreateOverrideCommand(
                req.bizIdentityCode(),
                req.coverageUuid(),
                req.overrideField(),
                req.overrideValue())));
  }

  @Operation(summary = "合并预览（只读；标准行实时 + 覆盖行叠加，同步标准无需写操作）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/preview")
  public ApiResponse<List<MergePreviewRow>> preview(
      @RequestParam String bizIdentityCode, @RequestParam String coverageUuid) {
    return ApiResponse.success(
        HttpStatus.OK.value(), overrideService.preview(bizIdentityCode, coverageUuid));
  }

  @Operation(summary = "受影响保单数（发布确认页）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/affected-count")
  public ApiResponse<AffectedPoliciesView> affectedCount(@RequestParam String bizIdentityCode) {
    return ApiResponse.success(
        HttpStatus.OK.value(), overrideService.affectedPolicies(bizIdentityCode));
  }

  @Operation(summary = "发布该主体该责任的全部未发布覆盖项（DISABLED→ACTIVE）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/publish")
  public ApiResponse<Integer> publish(
      @RequestParam String bizIdentityCode, @RequestParam String coverageUuid) {
    return ApiResponse.success(
        HttpStatus.OK.value(), overrideService.publish(bizIdentityCode, coverageUuid));
  }

  @Operation(summary = "停用单条覆盖项（回落标准）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{id}/disable")
  public ApiResponse<Void> disable(@PathVariable long id) {
    overrideService.disable(id);
    return ApiResponse.success(HttpStatus.OK.value(), null);
  }

  @Operation(summary = "生效配置（理算前消费；ACTIVE 覆盖 > 实时标准）")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/effective")
  public ApiResponse<EffectiveConfig> effective(
      @RequestParam String bizIdentityCode, @RequestParam String coverageUuid) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        effectiveConfigViewService.effectiveConfig(bizIdentityCode, coverageUuid));
  }

  public record UpsertReq(
      String bizIdentityCode, String coverageUuid, String overrideField, String overrideValue) {}
}
