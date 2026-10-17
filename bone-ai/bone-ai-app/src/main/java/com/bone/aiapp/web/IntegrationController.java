package com.bone.aiapp.web;

import com.bone.aiapp.config.BoneAiProperties;
import com.bone.aiintegration.application.IntegrationService;
import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.model.IntegrationMode;
import com.bone.core.model.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 生产拓扑：integration AI 节点（Shadow→Assisted→Automated 三态）HTTP 能力。 */
@RestController
@RequestMapping("/api/ai/v1/integration")
public class IntegrationController {

  private final IntegrationService integrationService;
  private final BoneAiProperties props;

  public IntegrationController(IntegrationService integrationService, BoneAiProperties props) {
    this.integrationService = integrationService;
    this.props = props;
  }

  @PostMapping("/invoke")
  public ApiResponse<ExternalCallResult> invoke(@RequestBody IntegrationInvokeRequest req) {
    IntegrationMode mode = req.mode() != null ? req.mode() : props.getDefaultIntegrationMode();
    ExternalCall call =
        new ExternalCall(
            req.callId(),
            req.tenantId(),
            req.systemId(),
            req.endpoint(),
            req.payload(),
            mode,
            req.correlationId());
    return ApiResponse.success(integrationService.invoke(call));
  }
}
