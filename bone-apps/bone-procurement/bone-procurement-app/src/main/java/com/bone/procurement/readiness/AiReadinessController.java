package com.bone.procurement.readiness;

import com.bone.core.model.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 采购 AI 就绪度只读端点。始终注册（不随治理开关关闭），用于在能力关闭/未配置时也能解释原因。 端点只读取运行期事实，不发起模型调用、不写状态。 */
@RestController
@RequestMapping("/api/v1/procurement")
public class AiReadinessController {

  private final AiReadinessApplicationService service;

  public AiReadinessController(AiReadinessApplicationService service) {
    this.service = service;
  }

  @GetMapping("/ai-readiness")
  @PreAuthorize("hasAuthority('procurement:ai-readiness:read')")
  public ApiResponse<AiReadinessView> readiness() {
    return ApiResponse.success(service.readiness());
  }
}
