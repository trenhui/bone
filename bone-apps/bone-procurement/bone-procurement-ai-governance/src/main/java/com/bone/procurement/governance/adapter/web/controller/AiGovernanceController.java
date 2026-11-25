package com.bone.procurement.governance.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.AiGovernanceProperties;
import com.bone.procurement.governance.application.CapabilityView;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.application.DriftMetricsView;
import com.bone.procurement.governance.application.HumanDecisionView;
import com.bone.procurement.governance.application.OversightMetricsView;
import com.bone.procurement.governance.domain.model.capability.DriftThresholds;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import com.bone.procurement.governance.domain.service.OversightThresholds;
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
  private final AiGovernanceProperties properties;

  public AiGovernanceController(
      AiGovernanceApplicationService service, AiGovernanceProperties properties) {
    this.service = service;
    this.properties = properties;
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

  /**
   * 漂移信号只读查询。阈值属 owner 通过线，只能由服务端配置属性提供（默认未配置 → UNDETERMINED）， 不接受客户端查询参数，避免具 read 权限者自设通过线绕过
   * fail-closed。
   */
  @GetMapping("/capabilities/{code}/drift")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<DriftMetricsView> drift(@PathVariable("code") String code) {
    DriftThresholds thresholds =
        (properties.getDriftThreshold() == null)
            ? DriftThresholds.unconfigured()
            : new DriftThresholds(
                properties.getDriftThreshold(), properties.getDriftMinSampleSize());
    return ApiResponse.success(DriftMetricsView.from(service.driftSignal(code, thresholds)));
  }

  /**
   * 人类监督有效性只读查询。阈值是 owner 通过线，只由服务端配置提供（缺省未配置 → UNDETERMINED）， 方法只接受路径参数，不接受客户端阈值注入，避免具 read
   * 权限者自设通过线绕过 fail-closed。
   */
  @GetMapping("/capabilities/{code}/oversight")
  @PreAuthorize("hasAuthority('procurement:ai-governance:read')")
  public ApiResponse<OversightMetricsView> oversight(@PathVariable("code") String code) {
    OversightThresholds thresholds =
        properties.getOversightReviewDurationFloorMs() == null
                || properties.getOversightFastReviewRatio() == null
            ? OversightThresholds.unconfigured()
            : new OversightThresholds(
                properties.getOversightReviewDurationFloorMs(),
                properties.getOversightMinSampleSize(),
                properties.getOversightFastReviewRatio());
    return ApiResponse.success(service.oversightSignal(code, thresholds));
  }

  @PostMapping("/human-decisions")
  @PreAuthorize("hasAuthority('procurement:ai-governance:write')")
  public ApiResponse<HumanDecisionView> recordHumanDecision(
      @Valid @RequestBody HumanDecisionRequest request) {
    return ApiResponse.success(service.recordHumanDecision(request.toCommand()));
  }
}
