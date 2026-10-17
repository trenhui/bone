package com.bone.aiapp.web;

import com.bone.aiapp.eval.application.EvalMetrics;
import com.bone.aiapp.eval.application.EvalRunMetricsService;
import com.bone.aiapp.eval.gateway.JudgeInput;
import com.bone.aiapp.eval.gateway.JudgePort;
import com.bone.aiapp.eval.gateway.JudgeVerdict;
import com.bone.core.model.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 生产拓扑：评测门禁（指标字典 + LLM-as-Judge 计量，详设 §2.7 / 3.4）。 */
@RestController
@RequestMapping("/api/ai/v1/eval")
public class EvalController {

  private final EvalRunMetricsService runMetricsService;
  private final JudgePort judgePort;

  public EvalController(EvalRunMetricsService runMetricsService, JudgePort judgePort) {
    this.runMetricsService = runMetricsService;
    this.judgePort = judgePort;
  }

  @GetMapping("/runs")
  public ApiResponse<EvalMetrics> runs(@RequestParam(required = false) String gate) {
    return ApiResponse.success(runMetricsService.metricsForGate(gate));
  }

  @PostMapping("/judge")
  public ApiResponse<JudgeVerdict> judge(@RequestBody JudgeInput input) {
    return ApiResponse.success(judgePort.judge(input));
  }
}
