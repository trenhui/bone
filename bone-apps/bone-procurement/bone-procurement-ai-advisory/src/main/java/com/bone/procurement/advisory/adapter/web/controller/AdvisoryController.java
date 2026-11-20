package com.bone.procurement.advisory.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.advisory.application.AdvisoryApplicationService;
import com.bone.procurement.advisory.domain.model.AdvisoryResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 业务 AI 建议裁决端点（纯工程骨架）：只返回建议态与标识，不返回模型输出。 */
@RestController
@RequestMapping("/api/v1/procurement/ai-advisory")
public class AdvisoryController {

  private final AdvisoryApplicationService service;

  public AdvisoryController(AdvisoryApplicationService service) {
    this.service = service;
  }

  @GetMapping("/capabilities")
  @PreAuthorize("hasAuthority('procurement:ai-advisory:read')")
  public ApiResponse<String[]> listCapabilities() {
    return ApiResponse.success(
        java.util.Arrays.stream(
                com.bone.procurement.advisory.domain.model.AdvisoryCapability.values())
            .map(com.bone.procurement.advisory.domain.model.AdvisoryCapability::codeValue)
            .toArray(String[]::new));
  }

  @GetMapping("/capabilities/{code}/advice")
  @PreAuthorize("hasAuthority('procurement:ai-advisory:read')")
  public ApiResponse<AdvisoryResponse> advice(@PathVariable("code") String code) {
    return ApiResponse.success(service.advise(code));
  }
}
