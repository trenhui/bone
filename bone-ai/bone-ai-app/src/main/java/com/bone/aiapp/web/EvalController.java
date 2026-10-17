package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.eval.application.EvalManagementApplicationService;
import com.bone.aiapp.eval.application.EvalMetrics;
import com.bone.aiapp.eval.application.EvalRunMetricsApplicationService;
import com.bone.aiapp.eval.domain.model.EvalCase;
import com.bone.aiapp.eval.domain.model.EvalRun;
import com.bone.aiapp.eval.gateway.JudgeInput;
import com.bone.aiapp.eval.gateway.JudgePort;
import com.bone.aiapp.eval.gateway.JudgeVerdict;
import com.bone.aiapp.web.EvalWriteRequests.EvalCaseRequest;
import com.bone.aiapp.web.EvalWriteRequests.EvalRunRequest;
import com.bone.core.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：评测门禁（指标字典 + LLM-as-Judge 计量 + 写入面，详设 §2.7 / 3.4）。
 *
 * <p>写入面（POST /cases、/runs）供 CI 门禁与评测管理回写运行记录——此前只有读端点，闭环断在最后一公里。
 */
@RestController
@RequestMapping("/api/ai/v1/eval")
public class EvalController {

  private final EvalRunMetricsApplicationService runMetricsService;
  private final EvalManagementApplicationService managementService;
  private final JudgePort judgePort;
  private final AiTenantContext tenantContext;

  public EvalController(
      EvalRunMetricsApplicationService runMetricsService,
      EvalManagementApplicationService managementService,
      JudgePort judgePort,
      AiTenantContext tenantContext) {
    this.runMetricsService = runMetricsService;
    this.managementService = managementService;
    this.judgePort = judgePort;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/runs")
  public ApiResponse<EvalMetrics> runs(@RequestParam(required = false) String gate) {
    return ApiResponse.success(runMetricsService.metricsForGate(gate));
  }

  @PostMapping("/cases")
  public ApiResponse<EvalCase> createCase(@RequestBody EvalCaseRequest request) {
    return ApiResponse.success(
        managementService.createCase(
            tenantContext.requireNumericTenantId(),
            request.scene(),
            request.caseType(),
            request.input(),
            request.expected(),
            request.expectedHit(),
            request.difficulty(),
            request.kappa()));
  }

  @PostMapping("/runs")
  public ApiResponse<EvalRun> createRun(@RequestBody EvalRunRequest request) {
    return ApiResponse.success(
        managementService.createRun(
            tenantContext.requireNumericTenantId(),
            request.gate(),
            request.caseId(),
            request.predicted(),
            request.expected(),
            request.expectedHit(),
            request.predictedHit(),
            request.citedClause(),
            request.judgeScore(),
            request.passed()));
  }

  @PostMapping("/judge")
  public ApiResponse<JudgeVerdict> judge(@RequestBody JudgeInput input) {
    return ApiResponse.success(judgePort.judge(input));
  }
}
