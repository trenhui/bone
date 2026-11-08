package com.bone.claim.adapter.web.controller;

import com.bone.claim.adapter.web.assembler.DecisionTraceAssembler;
import com.bone.claim.adapter.web.dto.response.DecisionRunResp;
import com.bone.claim.adapter.web.dto.response.DecisionTraceResp;
import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import com.bone.claim.application.DecisionRunApplicationService;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A3 决策执行接口（决策图一次执行 + 轨迹回放）。
 *
 * <p>执行落 {@code claim_decision_execution} + {@code claim_decision_trace}（append-only）， 按 traceId
 * 一键回放决策路径（证据 → 解析 → 核责 → 理算 → 决策）。
 */
@Tag(name = "智能理赔-决策执行", description = "决策图执行 / 轨迹回放")
@RestController
@RequestMapping("/api/v1/claims/{claimId}/decision")
@RequiredArgsConstructor
public class DecisionController {

  private final DecisionRunApplicationService decisionRunApplicationService;
  private final DecisionExecutionApplicationService decisionExecutionApplicationService;
  private final IdempotencyService idempotencyService;

  public record ExecuteReq(
      String graphVersion,
      String ruleVersion,
      String modelVersion,
      String promptVersion,
      String catalogVersion) {}

  @Operation(
      summary = "执行决策图",
      description =
          "同案仅一个活跃执行；结论由确定性分诊计算；AUTO_APPROVE 时按开关尝试自动结案并建结算单。"
              + "写接口幂等：同 Idempotency-Key 重复请求返回首次结果（防自动结案/建单被重试重复触发）")
  @PreAuthorize("hasAuthority('claim:decision:write')")
  @PostMapping("/execute")
  public ApiResponse<DecisionRunResp> execute(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "幂等键（UUID v4）；不传则不启用幂等")
          @RequestHeader(value = IdempotencyService.IDEMPOTENCY_KEY_HEADER, required = false)
          String idempotencyKey,
      @RequestBody(required = false) ExecuteReq request,
      HttpServletRequest httpRequest) {
    ExecuteReq req = request == null ? new ExecuteReq("v1", "v1", "", "", "") : request;
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

  @Operation(summary = "轨迹回放", description = "按 traceId 取全链节点序列")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/trace")
  public ApiResponse<List<DecisionTraceResp>> replay(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "追溯ID") @RequestParam("traceId") String traceId) {
    return ApiResponse.success(
        DecisionTraceAssembler.toResp(decisionExecutionApplicationService.replay(traceId)));
  }

  /** 幂等重放：快照里存的是首次成功响应（含统一信封），直接还原为同一响应体。 */
  @SuppressWarnings("unchecked")
  private static ApiResponse<DecisionRunResp> replayOf(
      IdempotencyService.ReplayedResponse snapshot) {
    return (ApiResponse<DecisionRunResp>) snapshot.body();
  }
}
