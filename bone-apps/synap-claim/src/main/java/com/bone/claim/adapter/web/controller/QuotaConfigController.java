package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.application.QuotaConfigApplicationService;
import com.bone.claim.application.QuotaConfigApplicationService.QuotaAccountView;
import com.bone.claim.application.QuotaConfigApplicationService.QuotaChangeView;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 额度配置接口（运营面）：开户 / 调额 / 减人 / 恢复 / 保全记录。
 *
 * <p><b>与 {@code GET /api/v1/claims/{claimId}/quota} 的分工</b>：后者是<b>理赔作业面</b>的只读视图
 * （"这件赔案占了多少额度"），本类是<b>运营配置面</b>的读写（"这个被保人有多少额度、保额改了多少"）。
 * 两者读的是同一张表，但口径与用途不同，混在一个端点里会让作业页多出改额度的能力（越权）。
 *
 * <p><b>操作人</b>：取自已验签 token（{@link CurrentOperator#requireId()}），不接受客户端声明—— 额度变更是资金相关动作，留痕必须可信。
 */
@Tag(name = "智能理赔-额度配置", description = "个人额度开户、调额、减人与保全记录")
@RestController
@RequestMapping("/api/v1/claim-config/quota-accounts")
@RequiredArgsConstructor
public class QuotaConfigController {

  private final QuotaConfigApplicationService quotaConfigApplicationService;

  @Operation(summary = "额度账户列表", description = "按保单号查该保单下的全部额度账户")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping
  public ApiResponse<List<QuotaAccountView>> accounts(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo) {
    return ApiResponse.success(
        HttpStatus.OK.value(), quotaConfigApplicationService.listByPolicy(policyNo));
  }

  @Operation(summary = "某被保人的额度账户", description = "按保单号 + 被保人假名化ID查")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping("/by-insured")
  public ApiResponse<List<QuotaAccountView>> accountsByInsured(
      @Parameter(description = "保单号") @ModelAttribute("policyNo") String policyNo,
      @Parameter(description = "被保人假名化ID") @ModelAttribute("insuredIdHash") String insuredIdHash) {
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.listByPolicyAndInsured(policyNo, insuredIdHash));
  }

  @Operation(summary = "额度保全记录", description = "某额度账户的历次变更（谁、何时、从多少改到多少）")
  @PreAuthorize("hasAuthority('claim:quota:read')")
  @GetMapping("/{quotaAccountId}/changes")
  public ApiResponse<List<QuotaChangeView>> changes(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId) {
    return ApiResponse.success(
        HttpStatus.OK.value(), quotaConfigApplicationService.listChanges(quotaAccountId));
  }

  @Operation(summary = "开户（初始化个人额度）", description = "同键重复调用返回既有账户（幂等）")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<QuotaAccountView> open(
      @Valid @RequestBody ClaimConfigRequests.OpenQuotaReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        quotaConfigApplicationService.openAccount(
            new QuotaConfigApplicationService.OpenQuota(
                request.insuredIdHash(),
                request.policyNo(),
                request.planId(),
                request.quotaYear() == null ? 0 : request.quotaYear(),
                request.quotaType() == null || request.quotaType().isBlank()
                    ? "PERSONAL"
                    : request.quotaType(),
                request.liabilityUuid(),
                request.quotaInitial(),
                operatorId,
                operatorId,
                request.remark())));
  }

  @Operation(summary = "调额", description = "改初始额度；下调不得低于已占用额度（已赔+已冻结），否则守恒被破坏")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/adjust")
  public ApiResponse<QuotaAccountView> adjust(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId,
      @Valid @RequestBody ClaimConfigRequests.AdjustQuotaReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.adjustInitial(
            new QuotaConfigApplicationService.AdjustQuota(
                quotaAccountId,
                request.newInitial(),
                operatorId,
                operatorId,
                request.remark(),
                request.version())));
  }

  @Operation(summary = "减人", description = "该被保人不再参与额度累计（不删除账户与历史）")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/remove")
  public ApiResponse<QuotaAccountView> remove(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId,
      @Valid @RequestBody ClaimConfigRequests.InsuredStateReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.removeInsured(
            new QuotaConfigApplicationService.AdjustQuota(
                quotaAccountId,
                null,
                operatorId,
                operatorId,
                request.remark(),
                request.version())));
  }

  @Operation(summary = "恢复", description = "撤销减人，重新参与额度累计")
  @PreAuthorize("hasAuthority('claim:quota:write')")
  @PostMapping("/{quotaAccountId}/restore")
  public ApiResponse<QuotaAccountView> restore(
      @Parameter(description = "额度账户ID") @PathVariable long quotaAccountId,
      @Valid @RequestBody ClaimConfigRequests.InsuredStateReq request) {
    String operatorId = CurrentOperator.requireId();
    return ApiResponse.success(
        HttpStatus.OK.value(),
        quotaConfigApplicationService.restoreInsured(
            new QuotaConfigApplicationService.AdjustQuota(
                quotaAccountId,
                null,
                operatorId,
                operatorId,
                request.remark(),
                request.version())));
  }
}
