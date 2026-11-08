package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.IndemnityApplicationService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A6 理算接口（金额计算的唯一入口，AI 不参与）。
 *
 * <p>返回结果含 {@code formulaSnapshot} 与逐行归因成分——拒赔五要素「事实认定」直接引用它， 而非另写自然语言（§8.3 层级结果码与归因链）。
 */
@Tag(name = "智能理赔-理算", description = "理算执行/重算/确认与结果查询")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/adjust")
@RequiredArgsConstructor
public class AdjustmentController {

  private final IndemnityApplicationService indemnityApplicationService;

  @Operation(summary = "执行理算", description = "前置：证据已锁定 + 计划存在生效责任；产出即 FROZEN")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "理算完成"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "SC_CLAIM_ADJUST_EVIDENCE_NOT_LOCKED / SC_CLAIM_LIABILITY_NOT_FOUND")
  })
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping
  public ApiResponse<IndemnityApplicationService.AdjustmentResult> calculate(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "规则版本（留空取当前）")
          @RequestHeader(value = "X-Rule-Version", required = false)
          String ruleVersion) {
    return ApiResponse.success(indemnityApplicationService.calculate(claimId, ruleVersion));
  }

  @Operation(summary = "重算", description = "规则/责任版本变更后重算；已确认 CONFIRMED 拒绝")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/recalculate")
  public ApiResponse<IndemnityApplicationService.AdjustmentResult> recalculate(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "规则版本") @RequestHeader(value = "X-Rule-Version", required = false)
          String ruleVersion) {
    return ApiResponse.success(indemnityApplicationService.recalculate(claimId, ruleVersion));
  }

  @Operation(summary = "理算确认", description = "核赔通过：理算结果锁定（额度占额在 A7 资金阶段）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/confirm")
  public ApiResponse<Void> confirm(@Parameter(description = "案件ID") @PathVariable long claimId) {
    indemnityApplicationService.confirm(claimId);
    return ApiResponse.success();
  }

  @Operation(summary = "查询最新理算结果", description = "含公式快照与逐行归因；未理算返回 null data")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<IndemnityApplicationService.AdjustmentResult> latest(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(indemnityApplicationService.latest(claimId));
  }
}
