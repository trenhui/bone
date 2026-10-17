package com.bone.aiapp.web;

import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.application.OrchestrationService;
import com.bone.core.model.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 生产拓扑：AI 编排流水线（规则预检 → 命中短路｜LLM → trace 留痕）。 */
@RestController
@RequestMapping("/api/ai/v1/pipeline")
public class PipelineController {

  private final OrchestrationService orchestration;

  public PipelineController(OrchestrationService orchestration) {
    this.orchestration = orchestration;
  }

  @PostMapping
  public ApiResponse<OrchestrationResult> run(@RequestBody OrchestrationRequest request) {
    return ApiResponse.success(orchestration.run(request));
  }
}
