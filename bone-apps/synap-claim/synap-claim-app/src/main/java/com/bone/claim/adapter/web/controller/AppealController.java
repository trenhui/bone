package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AppealApplicationService;
import com.bone.claim.application.AppealApplicationService.AppealResult;
import com.bone.claim.domain.model.appeal.AppealReviewResult;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A8 申诉接口（结案/拒赔生效后 48h 内受理；复议维持或推翻重启审核）。 */
@Tag(name = "智能理赔-申诉复议", description = "发起申诉 / 复议结论 / 查询")
@RestController
@RequestMapping(value = {"/api/v1/claims/{claimId}/appeal", "/api/v1/claims/{claimId}/appeals"})
@RequiredArgsConstructor
public class AppealController {

  private final AppealApplicationService appealApplicationService;

  public record SubmitAppealReq(String reason) {}

  public record ReviewAppealReq(AppealReviewResult result, String reason) {}

  @Operation(summary = "发起申诉（受理）", description = "须已终态且未超 48h；幂等")
  @PreAuthorize("hasAuthority('claim:appeal:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<AppealResult> submit(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody SubmitAppealReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        appealApplicationService.submit(claimId, CurrentOperator.requireId(), request.reason()));
  }

  @Operation(
      summary = "复议结论",
      description = "UPHELD 维持(→CLOSED_REJECTED) / REOPEN 推翻重启审核(→ADJUSTING)")
  @PreAuthorize("hasAuthority('claim:appeal:write')")
  @PostMapping("/review")
  public ApiResponse<AppealResult> review(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody ReviewAppealReq request) {
    return ApiResponse.success(
        appealApplicationService.review(
            claimId, request.result(), CurrentOperator.requireId(), request.reason()));
  }

  @Operation(summary = "查询最新申诉")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<AppealResult> latest(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(appealApplicationService.latest(claimId));
  }
}
