package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.SettlementApplicationService;
import com.bone.claim.application.SettlementApplicationService.SettlementResult;
import com.bone.claim.domain.model.settlement.AccountType;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

  public record AuditPassReq(List<String> reviewerIds, String firstReviewerId) {}

  public record CancelReq(Boolean payFailed) {}

  @Operation(summary = "建结算单（软占额度）", description = "幂等：同 claim+attempt 重复建单返回首次结果")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ApiResponse<SettlementResult> create(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody CreateSettlementReq request,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    return ApiResponse.success(
        HttpStatus.CREATED.value(),
        settlementApplicationService.create(
            claimId, request.attempt(), request.accountType(), operatorId));
  }

  @Operation(summary = "审核通过（硬预占/≥1万双人复核）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/audit-pass")
  public ApiResponse<SettlementResult> auditPass(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody AuditPassReq request,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    return ApiResponse.success(
        settlementApplicationService.auditPass(
            claimId, operatorId, request.reviewerIds(), request.firstReviewerId()));
  }

  @Operation(summary = "支付成功（额度 frozen→claim）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/pay")
  public ApiResponse<SettlementResult> pay(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    return ApiResponse.success(settlementApplicationService.pay(claimId, operatorId));
  }

  @Operation(summary = "撤销/拒赔/支付失败（释放预占）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/cancel")
  public ApiResponse<SettlementResult> cancel(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Valid @RequestBody CancelReq request,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    return ApiResponse.success(
        settlementApplicationService.cancel(
            claimId, operatorId, Boolean.TRUE.equals(request.payFailed())));
  }

  @Operation(summary = "已付退票退款（红冲并回冲额度）")
  @PreAuthorize("hasAuthority('claim:settlement:write')")
  @PostMapping("/refund")
  public ApiResponse<SettlementResult> refund(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "操作人") @RequestHeader(value = "X-Operator-Id", required = false)
          String operatorId) {
    return ApiResponse.success(settlementApplicationService.refund(claimId, operatorId));
  }

  @Operation(summary = "查询最新结算单")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping
  public ApiResponse<SettlementResult> latest(
      @Parameter(description = "案件ID") @PathVariable long claimId) {
    return ApiResponse.success(settlementApplicationService.latest(claimId));
  }
}
