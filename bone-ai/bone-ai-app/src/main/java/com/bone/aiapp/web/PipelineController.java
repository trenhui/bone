package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.core.model.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：AI 编排流水线（规则预检 → 命中短路｜LLM → trace 留痕）。
 *
 * <p>租户一律从 {@link TenantContext} 取并覆盖请求体值（详设 §3.1「禁止入参传租户」）——body 里的 tenantId 视为不可信。
 */
@RestController
@RequestMapping("/api/ai/v1/pipeline")
public class PipelineController {

  private final OrchestrationApplicationService orchestration;
  private final AiTenantContext tenantContext;

  public PipelineController(
      OrchestrationApplicationService orchestration, AiTenantContext tenantContext) {
    this.orchestration = orchestration;
    this.tenantContext = tenantContext;
  }

  @PostMapping
  public ApiResponse<OrchestrationResult> run(@RequestBody OrchestrationRequest request) {
    OrchestrationRequest scoped =
        new OrchestrationRequest(
            tenantContext.requireTenantId(),
            request.ruleCodes(),
            request.context(),
            request.llmRequest(),
            request.trace(),
            request.traceId());
    return ApiResponse.success(orchestration.run(scoped));
  }
}
