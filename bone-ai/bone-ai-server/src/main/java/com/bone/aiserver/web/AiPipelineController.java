package com.bone.aiserver.web;

import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.application.OrchestrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参考宿主演示端点：把编排流水线（规则预检 → 命中短路｜LLM → trace 留痕）以 HTTP 暴露，供产品团队对照接线。
 *
 * <p>成本红线由编排层强制（规则命中即跳过 LLM）；LLM 真实调用由 bone-ai-gateway 收口并发布 {@code LlmCallRecorded}，bone-ai-trace
 * 监听落证据快照——本端点不感知这些细节。
 */
@RestController
@RequestMapping("/api/ai/v1/pipeline")
public class AiPipelineController {

  private final OrchestrationService orchestration;

  public AiPipelineController(OrchestrationService orchestration) {
    this.orchestration = orchestration;
  }

  @PostMapping
  public OrchestrationResult run(@RequestBody OrchestrationRequest request) {
    return orchestration.run(request);
  }
}
