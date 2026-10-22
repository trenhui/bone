package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.DecisionExecutionApplicationService;
import com.bone.claim.application.DecisionGraphExecutor;
import com.bone.claim.application.DecisionGraphExecutor.DecisionExecutionResult;
import com.bone.claim.application.DecisionGraphExecutor.DecisionVersions;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  private final DecisionGraphExecutor decisionGraphExecutor;
  private final DecisionExecutionApplicationService decisionExecutionApplicationService;

  public record ExecuteReq(
      String graphVersion,
      String ruleVersion,
      String modelVersion,
      String promptVersion,
      String catalogVersion) {}

  @Operation(summary = "执行决策图", description = "同案仅一个活跃执行；结论由确定性分诊计算")
  @PreAuthorize("hasAuthority('claim:decision:write')")
  @PostMapping("/execute")
  public ApiResponse<DecisionExecutionResult> execute(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @RequestBody(required = false) ExecuteReq request) {
    ExecuteReq req = request == null ? new ExecuteReq("v1", "v1", "", "", "") : request;
    return ApiResponse.success(
        decisionGraphExecutor.execute(
            claimId,
            new DecisionVersions(
                req.graphVersion(),
                req.ruleVersion(),
                req.modelVersion(),
                req.promptVersion(),
                req.catalogVersion())));
  }

  @Operation(summary = "轨迹回放", description = "按 traceId 取全链节点序列")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/trace")
  public ApiResponse<List<DecisionTraceNode>> replay(
      @Parameter(description = "案件ID") @PathVariable long claimId,
      @Parameter(description = "追溯ID") @RequestParam("traceId") String traceId) {
    return ApiResponse.success(decisionExecutionApplicationService.replay(traceId));
  }
}
