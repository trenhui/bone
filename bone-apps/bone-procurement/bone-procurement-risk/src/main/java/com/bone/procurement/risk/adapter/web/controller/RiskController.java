package com.bone.procurement.risk.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.risk.application.RiskApplicationService;
import com.bone.procurement.risk.domain.model.PerformanceScore;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 风险绩效 HTTP 端点（对齐 openapi.yaml §03 卷占位路径）。 */
@RestController
@RequestMapping("/api/v1/procurement")
public class RiskController {

  private final RiskApplicationService service;

  public RiskController(RiskApplicationService service) {
    this.service = service;
  }

  @PostMapping("/risk-alerts")
  @PreAuthorize("hasAuthority('procurement:risk-alert:write')")
  public ApiResponse<Long> raise(@Valid @RequestBody RaiseRiskAlertRequest request) {
    return ApiResponse.success(
        service.raise(
            request.tenantId(),
            request.supplierId(),
            request.source(),
            request.sourceRef(),
            request.severity(),
            request.alertType(),
            request.evidenceRef(),
            request.detectedAt()));
  }

  @GetMapping("/performance-scores")
  @PreAuthorize("hasAuthority('procurement:performance-score:read')")
  public ApiResponse<List<PerformanceScore>> scores(
      @RequestParam("tenantId") Long tenantId, @RequestParam("supplierId") String supplierId) {
    return ApiResponse.success(service.scores(tenantId, supplierId));
  }

  public record RaiseRiskAlertRequest(
      Long tenantId,
      String supplierId,
      String source,
      String sourceRef,
      String severity,
      String alertType,
      String evidenceRef,
      Date detectedAt) {}
}
