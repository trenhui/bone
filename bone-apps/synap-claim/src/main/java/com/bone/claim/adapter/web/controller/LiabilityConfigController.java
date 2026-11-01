package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests;
import com.bone.claim.application.LiabilityConfigApplicationService;
import com.bone.claim.application.LiabilityConfigApplicationService.LiabilityView;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 责任配置接口（运营面，A4 配置端）。
 *
 * <p><b>命名空间</b>：{@code /api/v1/claim-config/liabilities}，挂在保单配置命名空间下， 与计划/险种配置同族。权限码复用 {@code
 * claim:policy:read/write}（责任是保单配置的一部分）。
 *
 * <p><b>版本化</b>：编辑已生效责任会返回<b>新版本</b>（DRAFT）而原版本冻结；发布新版本会自动下线同 UUID 旧 ACTIVE 版本。
 */
@Tag(name = "智能理赔-责任配置", description = "保险责任的配置、版本化与发布")
@RestController
@RequestMapping("/api/v1/claim-config/liabilities")
@RequiredArgsConstructor
public class LiabilityConfigController {

  private final LiabilityConfigApplicationService liabilityConfigApplicationService;

  @Operation(summary = "新建责任", description = "草稿态新建一条责任配置（版本 v1）")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping
  public ApiResponse<LiabilityView> createLiability(
      @Valid @RequestBody LiabilityRequests.CreateLiabilityReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(), liabilityConfigApplicationService.createLiability(request));
  }

  @Operation(summary = "编辑责任", description = "ACTIVE 责任自动生成新版本草稿，DRAFT 原地编辑；空字段沿用原值")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PutMapping("/{liabilityId}")
  public ApiResponse<LiabilityView> updateLiability(
      @Parameter(description = "责任ID") @PathVariable long liabilityId,
      @Valid @RequestBody LiabilityRequests.UpdateLiabilityReq request) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        liabilityConfigApplicationService.updateLiability(liabilityId, request));
  }

  @Operation(summary = "发布责任", description = "DRAFT → ACTIVE；同 UUID 旧 ACTIVE 版本自动下线")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{liabilityId}/publish")
  public ApiResponse<LiabilityView> publishLiability(
      @Parameter(description = "责任ID") @PathVariable long liabilityId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), liabilityConfigApplicationService.publishLiability(liabilityId));
  }

  @Operation(summary = "下线责任")
  @PreAuthorize("hasAuthority('claim:policy:write')")
  @PostMapping("/{liabilityId}/retire")
  public ApiResponse<LiabilityView> retireLiability(
      @Parameter(description = "责任ID") @PathVariable long liabilityId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), liabilityConfigApplicationService.retireLiability(liabilityId));
  }

  @Operation(summary = "按计划列出责任", description = "返回该计划下全部责任（含各版本），供配置面树形展示")
  @PreAuthorize("hasAuthority('claim:policy:read')")
  @GetMapping("/plan/{planId}")
  public ApiResponse<List<LiabilityView>> listByPlan(
      @Parameter(description = "计划ID") @PathVariable long planId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), liabilityConfigApplicationService.listByPlan(planId));
  }
}
