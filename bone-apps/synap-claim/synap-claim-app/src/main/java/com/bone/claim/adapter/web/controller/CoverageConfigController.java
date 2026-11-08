package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.application.ClaimConfigApplicationService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 险种配置端点；契约路径由前端 {@code claimConfigApi.ts} 与配置面 E2E 定死。 */
@Tag(name = "智能理赔-险种配置", description = "某计划下险种的查询、新建、编辑与发布下线")
@RestController
@RequestMapping("/api/v1/claim-config/coverages")
@RequiredArgsConstructor
public class CoverageConfigController {

  private final ClaimConfigApplicationService claimConfigApplicationService;

  @Operation(summary = "某计划下的险种列表")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping({"", "/"})
  public ApiResponse<List<ClaimConfigApplicationService.CoverageView>> list(
      @RequestParam long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.listCoverages(planId));
  }

  @Operation(summary = "新建险种", description = "草稿态；同计划下险种编码唯一，重复返回 409")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description = "COMMON_VALIDATION_FAILED：必填字段缺失")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "SC_CLAIM_POLICY_DUPLICATED / PLAN_DUPLICATED / COVERAGE_DUPLICATED：业务键重复")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping({"", "/"})
  public ApiResponse<ClaimConfigApplicationService.CoverageView> create(
      @Valid @RequestBody ClaimConfigRequests.CreateCoverageReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        claimConfigApplicationService.createCoverage(
            new ClaimConfigApplicationService.CreateCoverage(
                request.planId(),
                request.coverageCode(),
                request.coverageName(),
                request.coverageLimit(),
                request.liabilityType())));
  }

  @Operation(summary = "编辑险种", description = "不改险种编码与状态；version 用于并发冲突检测")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/{coverageId}")
  public ApiResponse<ClaimConfigApplicationService.CoverageView> update(
      @Parameter(description = "险种ID") @PathVariable long coverageId,
      @Valid @RequestBody ClaimConfigRequests.UpdateCoverageReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        claimConfigApplicationService.updateCoverage(
            coverageId,
            new ClaimConfigApplicationService.UpdateCoverage(
                request.coverageName(),
                request.coverageLimit(),
                request.liabilityType(),
                request.version())));
  }

  @Operation(summary = "发布险种", description = "DRAFT → ACTIVE")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{coverageId}/publish")
  public ApiResponse<ClaimConfigApplicationService.CoverageView> publish(
      @Parameter(description = "险种ID") @PathVariable long coverageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.publishCoverage(coverageId));
  }

  @Operation(summary = "下线险种")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{coverageId}/retire")
  public ApiResponse<ClaimConfigApplicationService.CoverageView> retire(
      @Parameter(description = "险种ID") @PathVariable long coverageId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), claimConfigApplicationService.retireCoverage(coverageId));
  }
}
