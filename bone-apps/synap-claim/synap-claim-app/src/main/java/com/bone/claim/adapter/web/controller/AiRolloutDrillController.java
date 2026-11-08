package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiRolloutDrillApplicationService;
import com.bone.claim.domain.model.config.AiRolloutDrill;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回滚演练只读端点：只推演回退一档后的状态完整性，不执行降档、不写任何配置。
 *
 * <p>与 {@link AiRolloutController} 同一治理形态：不提供 promote / rollback 写端点，
 * 人工降档仍走既有开关配置入口，避免「页面点上去、准入逻辑不认」的双写分叉。
 */
@Tag(name = "智能理赔-AI治理", description = "AI 放量回滚演练（只读 dry-run）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiRolloutDrillController {

  private final AiRolloutDrillApplicationService drillService;

  @Operation(
      summary = "AI 放量回滚演练（只读）",
      description = "推演从当前档位回退一档后的资金权限、模型参与要求与状态完整性；" + "不执行降档、不修改任何配置，mutations 恒为 0。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-rollout/drill")
  public ApiResponse<AiRolloutDrill.Report> drill() {
    return ApiResponse.success(drillService.drill());
  }
}
