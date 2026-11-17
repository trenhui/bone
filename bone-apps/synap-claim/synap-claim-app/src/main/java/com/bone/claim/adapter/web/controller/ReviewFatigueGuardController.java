package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.ReviewFatigueGuardService;
import com.bone.claim.domain.model.review.ReviewFatigueDecision;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核疲劳评估门面（只读 soft prompt）。
 *
 * <p>挂在已登记网关白名单的 {@code /api/v1/review/**} 前缀下；操作人取已验签的 {@link CurrentOperator}，
 * 不接受请求体/查询参数声明。本控制器不写库、不改案件状态、不触发转派。
 */
@Tag(name = "智能理赔-审核疲劳防控", description = "只读评估（soft prompt，不阻断人工终审）")
@RestController
@RequestMapping("/api/v1/review/fatigue-guard")
@RequiredArgsConstructor
public class ReviewFatigueGuardController {

  private final ReviewFatigueGuardService reviewFatigueGuardService;

  @Operation(summary = "审核疲劳评估", description = "返回是否超量/是否提示休息/敏感案件是否适合派单；连续时长与班末分钟由调用方给出")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/evaluation")
  public ApiResponse<ReviewFatigueDecision> evaluation(
      @RequestParam(required = false) Integer continuousMinutes,
      @RequestParam(required = false) Integer minutesToShiftEnd,
      @RequestParam(required = false) BigDecimal claimAmount) {
    return ApiResponse.success(
        reviewFatigueGuardService.evaluate(
            CurrentOperator.requireId(), continuousMinutes, minutesToShiftEnd, claimAmount));
  }
}
