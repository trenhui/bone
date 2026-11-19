package com.bone.procurement.governance.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.application.KillSwitchView;
import com.bone.procurement.governance.application.SafetyApplicationService;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import com.bone.procurement.governance.domain.model.labeling.DeliveryDecision;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 安全治理端点：全域 kill switch / 内容双标识出域拦截 / G15 公平裁决。能力开关关闭时不注册。 */
@RestController
@RequestMapping("/api/v1/procurement/ai-governance/safety")
@ConditionalOnAiGovernanceEnabled
public class SafetyController {

  private final SafetyApplicationService service;

  public SafetyController(SafetyApplicationService service) {
    this.service = service;
  }

  @GetMapping("/kill-switch")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<KillSwitchView> currentKillSwitch() {
    return ApiResponse.success(service.currentKillSwitch());
  }

  @PostMapping("/kill-switch/engage")
  @PreAuthorize("hasAuthority('procurement:ai-governance:write')")
  public ApiResponse<KillSwitchView> engage(@Valid @RequestBody KillSwitchRequest request) {
    return ApiResponse.success(service.engageKillSwitch(request.reason(), request.drillRef()));
  }

  @PostMapping("/kill-switch/disengage")
  @PreAuthorize("hasAuthority('procurement:ai-governance:write')")
  public ApiResponse<KillSwitchView> disengage(@Valid @RequestBody KillSwitchRequest request) {
    return ApiResponse.success(service.disengageKillSwitch(request.reason()));
  }

  @GetMapping("/kill-switch/drill-status")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<List<KillSwitchView.CapabilityEffect>> drillStatus() {
    return ApiResponse.success(service.drillStatus());
  }

  @PostMapping("/content-labels/check")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<DeliveryDecision> checkDelivery(
      @Valid @RequestBody ContentLabelRequest request) {
    return ApiResponse.success(service.checkDelivery(request.toLabel()));
  }

  @PostMapping("/fairness/evaluate")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<FairnessDecision> evaluateFairness(
      @Valid @RequestBody FairnessRequest request) {
    return ApiResponse.success(service.evaluateFairness(request.toSignal()));
  }
}
