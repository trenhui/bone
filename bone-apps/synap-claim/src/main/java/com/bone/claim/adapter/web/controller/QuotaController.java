package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.QuotaQueryService;
import com.bone.claim.application.QuotaQueryService.QuotaSummary;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A5 额度查询接口（只读；资金工作台展示额度三值与流水）。 */
@Tag(name = "智能理赔-额度账户", description = "按案件查询额度账户与流水")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/quota")
@RequiredArgsConstructor
public class QuotaController {

  private final QuotaQueryService quotaQueryService;

  @Operation(
      summary = "查询案件额度账户与流水",
      description = "返回个人额度三值（initial/claimed/frozen，可用=initial−claimed−frozen）与 append-only 流水")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<QuotaSummary> quota(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(HttpStatus.OK.value(), quotaQueryService.getByClaim(claimId));
  }
}
