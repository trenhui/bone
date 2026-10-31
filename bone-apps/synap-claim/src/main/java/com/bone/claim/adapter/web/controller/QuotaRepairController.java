package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.QuotaProjectionRepairService;
import com.bone.claim.application.QuotaProjectionRepairService.RepairResult;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 额度投影修复接口（运维面，§11.7 不变量 1 ①的处置动作）。
 *
 * <p><b>为什么是 POST 而不是"改数"的 PUT</b>：这个端点没有请求体。它能做的只有一件确定的事——按 {@code t_quota_transaction} 重算 {@code
 * quota_account} 的四个派生计数。任何"把某列改成 X"的请求都不该存在： 那已经是改账，须走补偿流水与双人复核，不是维护动作。
 *
 * <p><b>操作人</b>取自已验签 token（{@link CurrentOperator#requireId()}），不接受客户端声明—— 重算会改变账面数值，留痕必须可信。
 */
@Tag(name = "智能理赔-额度运维", description = "按流水重建额度账户投影（消除 PROJECTION_DRIFT）")
@RestController
@RequestMapping("/api/v1/claim-ops/quota-accounts")
@RequiredArgsConstructor
public class QuotaRepairController {

  private final QuotaProjectionRepairService quotaProjectionRepairService;

  @Operation(
      summary = "按流水重建账户投影",
      description =
          "以 t_quota_transaction 为事实重算 frozen/claimed/deductible_used/cap_used。"
              + "无漂移时 changed=false；流水为空而投影非零、或重算后超出初始额度时拒绝（409）。")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/projection/rebuild")
  public ApiResponse<RepairResult> rebuild(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId) {
    RepairResult result =
        quotaProjectionRepairService.rebuildProjection(quotaAccountId, CurrentOperator.requireId());
    return ApiResponse.success(HttpStatus.OK.value(), result);
  }
}
