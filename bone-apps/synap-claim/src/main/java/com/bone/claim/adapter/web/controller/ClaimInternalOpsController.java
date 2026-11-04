package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.dto.response.DecisionRunResp;
import com.bone.claim.application.AdjustmentOpsApplicationService;
import com.bone.claim.application.AdjustmentOpsApplicationService.RecomputeReq;
import com.bone.claim.application.AdjustmentOpsApplicationService.RegressionReq;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import com.bone.claim.application.DecisionRunApplicationService;
import com.bone.claim.application.IndemnityApplicationService;
import com.bone.claim.application.ReconciliationApplicationService;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部运维接口（契约 §10.2：编排层 / 运维工具调用，非端侧业务面）。
 *
 * <p>五个端点与 §10.2 一一对应：理算试算比对 / 存量回溯重算 / 规则基准集回归 / 编排层触发决策图 / 资金日终对账报告查询。权限仍按理赔域权限码收口（内部 ≠ 免鉴权，
 * 服务间调用携带具身份），写接口照走 §10.3 幂等。
 */
@Tag(name = "智能理赔-内部运维", description = "§10.2 内部接口：试算/重算/回归/决策触发/对账查询")
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class ClaimInternalOpsController {

  private final IndemnityApplicationService indemnityApplicationService;
  private final AdjustmentOpsApplicationService adjustmentOpsApplicationService;
  private final DecisionRunApplicationService decisionRunApplicationService;
  private final ReconciliationApplicationService reconciliationApplicationService;
  private final IdempotencyService idempotencyService;

  /** 决策触发请求（版本可空 = 取当前生效版本，与对外执行端点同口径）。 */
  public record DecideReq(
      String graphVersion,
      String ruleVersion,
      String modelVersion,
      String promptVersion,
      String catalogVersion) {}

  @Operation(summary = "理算试算比对", description = "规则变更前试算：只算不落库，候选结果对照现行基线出差异")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/adjust/preview")
  public ApiResponse<IndemnityApplicationService.AdjustmentPreview> preview(
      @RequestBody PreviewReq req) {
    return ApiResponse.success(
        indemnityApplicationService.preview(req.claimId(), req.ruleVersion()));
  }

  /** 试算请求。 */
  public record PreviewReq(Long claimId, String ruleVersion) {}

  @Operation(
      summary = "存量回溯重算",
      description =
          "规则/责任版本发布后对存量案件回溯重算，出逐案差异报告；"
              + "dryRun=true 只算不落库，dryRun=false 逐案事务真重算（已 CONFIRMED 案件由聚合拒绝，绝不回写已付案件）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/adjust/recompute")
  public ApiResponse<List<AdjustmentOpsApplicationService.RecomputeItem>> recompute(
      @RequestBody RecomputeReq req) {
    return ApiResponse.success(adjustmentOpsApplicationService.recompute(req));
  }

  @Operation(
      summary = "规则基准集回归",
      description =
          "基准集逐案试算，实际输出与期望不符即失败（规则/责任版本发布门禁）。" + "一期覆盖理算引擎确定性输出（ADR-002 豁免：bone-ai-rules 端口未接线）")
  @PreAuthorize("hasAuthority('claim:review:write')")
  @PostMapping("/rules/regression")
  public ApiResponse<AdjustmentOpsApplicationService.RegressionReport> regression(
      @RequestBody RegressionReq req) {
    return ApiResponse.success(adjustmentOpsApplicationService.regression(req));
  }

  @Operation(
      summary = "触发决策图执行",
      description = "编排层入口：同案仅一个活跃执行；写接口幂等（同 Idempotency-Key 重复请求返回首次结果）")
  @PreAuthorize("hasAuthority('claim:decision:write')")
  @PostMapping("/claims/{claimId}/decide")
  public ApiResponse<DecisionRunResp> decide(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "幂等键（UUID v4）；不传则不启用幂等")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false)
          String idempotencyKey,
      @RequestBody(required = false) DecideReq request,
      HttpServletRequest httpRequest) {
    DecideReq req = request == null ? new DecideReq("v1", "v1", "", "", "") : request;
    Optional<IdempotencyService.ReplayedResponse> replayed =
        idempotencyService.replay(
            idempotencyKey,
            MDC.get("userId"),
            httpRequest.getMethod(),
            httpRequest.getRequestURI(),
            req);
    if (replayed.isPresent()) {
      return replayOf(replayed.get());
    }
    ApiResponse<DecisionRunResp> body =
        ApiResponse.success(
            DecisionRunResp.of(
                decisionRunApplicationService.execute(
                    claimId,
                    new DecisionVersions(
                        req.graphVersion(),
                        req.ruleVersion(),
                        req.modelVersion(),
                        req.promptVersion(),
                        req.catalogVersion()))));
    idempotencyService.remember(
        idempotencyKey,
        MDC.get("userId"),
        httpRequest.getMethod(),
        httpRequest.getRequestURI(),
        req,
        new IdempotencyService.ReplayedResponse(HttpStatus.OK.value(), null, body));
    return body;
  }

  @Operation(
      summary = "对账报告查询",
      description = "资金日终对账（QUOTA/PAYMENT/CASE_SETTLE 三通道）结果与差异明细；报告未生成返回空列表")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/reconciliation/{bizDate}")
  public ApiResponse<ReconciliationApplicationService.ReconciliationView> reconciliation(
      @Parameter(description = "业务日（yyyy-MM-dd）") @PathVariable LocalDate bizDate) {
    return ApiResponse.success(reconciliationApplicationService.findReportView(bizDate));
  }

  /** 幂等重放：快照里存的是首次成功响应（含统一信封），直接还原为同一响应体。 */
  @SuppressWarnings("unchecked")
  private static ApiResponse<DecisionRunResp> replayOf(
      IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<DecisionRunResp>) snapshot.body();
  }
}
