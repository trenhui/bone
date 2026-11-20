package com.bone.procurement.governance.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.CapabilityView;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.application.HumanDecisionView;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 决策治理 HTTP 端点。
 *
 * <p>写操作（登记 / 停用 / 降级 / 启用 / 人工终决）需 write 权限；读操作（清单 / 详情 / 裁决 / 追溯 / override 计数）需 read 权限。
 * 能力开关关闭时全部端点不注册。
 */
@RestController
@RequestMapping("/api/v1/procurement/ai-governance")
@ConditionalOnAiGovernanceEnabled
public class AiGovernanceController {

  private final AiGovernanceApplicationService service;

  public AiGovernanceController(AiGovernanceApplicationService service) {
    this.service = service;
  }

  @PostMapping("/capabilities")
  @PreAuthorize("hasAuthority('procurement:ai-governance:write')")
  public ApiResponse<CapabilityView> register(
      @Valid @RequestBody RegisterCapabilityRequest request) {
    return ApiResponse.success(service.register(request.toCommand()));
  }

  @GetMapping("/capabilities")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<List<CapabilityView>> listCapabilities() {
    return ApiResponse.success(service.listCapabilities());
  }

  @GetMapping("/capabilities/{code}")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<CapabilityView> getCapability(@PathVariable("code") String code) {
    return ApiResponse.success(service.getCapability(code));
  }

  @PostMapping("/capabilities/{code}/state")
  @PreAuthorize("hasAuthority('procurement:ai-governance:write')")
  public ApiResponse<CapabilityView> changeState(
      @PathVariable("code") String code, @Valid @RequestBody CapabilityStateRequest request) {
    return ApiResponse.success(service.changeState(request.action(), request.toCommand(code)));
  }

  @PostMapping("/capabilities/{code}/decide")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<GovernanceDecision> decide(@PathVariable("code") String code) {
    return ApiResponse.success(service.decide(code));
  }

  @GetMapping("/capabilities/{code}/trace")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<List<GovernanceTraceEntry>> trace(@PathVariable("code") String code) {
    return ApiResponse.success(service.trace(code));
  }

  @GetMapping("/capabilities/{code}/overrides")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<Long> countOverrides(@PathVariable("code") String code) {
    return ApiResponse.success(service.countOverrides(code));
  }

  @PostMapping("/human-decisions")
  @PreAuthorize("hasAuthority('procurement:ai-governance:write')")
  public ApiResponse<HumanDecisionView> recordHumanDecision(
      @Valid @RequestBody HumanDecisionRequest request) {
    return ApiResponse.success(service.recordHumanDecision(request.toCommand()));
  }
}
