package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.SettlementApplicationService;
import com.bone.claim.application.SettlementApplicationService.SettlementResult;
import com.bone.claim.domain.model.settlement.AccountType;
import com.bone.core.model.ApiResponse;
import com.bone.core.security.auth.CurrentOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

/** A7 资金接口（两段式结算：软占 → 硬预占/复核 → 支付；额度占用随资金阶段经独立事务落账）。 */
@Tag(name = "智能理赔-资金结算", description = "建单/审核通过/支付/撤销/退款与状态查询")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/settlement")
@RequiredArgsConstructor
public class SettlementController {

  private final SettlementApplicationService settlementApplicationService;

  public record CreateSettlementReq(
      @NotNull(message = "占额周期序号必填") Integer attempt, AccountType accountType) {}

  /**
   * 审核通过请求：<b>无请求体</b>。
   *
   * <p>两道认证的身份都不来自调用方声明：审核人 = 当前 token 持有者，复核人 = 下一笔 {@code review-confirm} 的 token
   * 持有者；职责分离的比较基准（赔案第一审核人）由服务端从赔案聚合读取。 曾经的 {@code reviewerIds} 名单已删除——名单可自填，双人复核即退化为自我声明。
   */
  public record CancelReq(Boolean payFailed) {}

  @Operation(summary = "建结算单（软占额度）", description = "幂等：同 claim+attempt 重复建单返回首次结果")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<SettlementResult> create(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody CreateSettlementReq request) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        settlementApplicationService.create(
            claimId, request.attempt(), request.accountType(), CurrentOperator.requireId()));
  }

  @Operation(summary = "审核通过（第一道认证）", description = "小额直接硬预占；≥1 万进入复核冻结，须另一身份调用 /review-confirm 放行")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/audit-pass")
  public ApiResponse<SettlementResult> auditPass(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(
        settlementApplicationService.auditPass(claimId, CurrentOperator.requireId()));
  }

  @Operation(
      summary = "大额复核放行（第二道认证）",
      description = "REVIEW_FREEZE → HARD_RESERVED；复核人不得为审核发起人或该赔案的第一审核人")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/review-confirm")
  public ApiResponse<SettlementResult> confirmReview(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(
        settlementApplicationService.confirmReview(claimId, CurrentOperator.requireId()));
  }

  @Operation(summary = "支付成功（额度 frozen→claim）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/pay")
  public ApiResponse<SettlementResult> pay(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(
        settlementApplicationService.pay(claimId, CurrentOperator.requireId()));
  }

  @Operation(summary = "撤销/拒赔/支付失败（释放预占）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/cancel")
  public ApiResponse<SettlementResult> cancel(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody CancelReq request) {
    return ApiResponse.success(
        settlementApplicationService.cancel(
            claimId, CurrentOperator.requireId(), Boolean.TRUE.equals(request.payFailed())));
  }

  @Operation(summary = "已付退票退款（红冲并回冲额度）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/refund")
  public ApiResponse<SettlementResult> refund(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(
        settlementApplicationService.refund(claimId, CurrentOperator.requireId()));
  }

  @Operation(summary = "查询最新结算单")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<SettlementResult> latest(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(settlementApplicationService.latest(claimId));
  }
}
