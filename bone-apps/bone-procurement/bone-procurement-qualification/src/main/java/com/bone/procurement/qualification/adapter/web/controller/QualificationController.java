package com.bone.procurement.qualification.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.qualification.application.OnboardingApplicationService;
import com.bone.procurement.qualification.application.QualificationApplicationService;
import com.bone.procurement.qualification.domain.model.Qualification;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 准入 HTTP 端点（对齐 openapi.yaml §02 卷占位路径）。 */
@RestController
@RequestMapping("/api/v1/procurement")
public class QualificationController {

  private final QualificationApplicationService qualificationService;
  private final OnboardingApplicationService onboardingService;

  public QualificationController(
      QualificationApplicationService qualificationService,
      OnboardingApplicationService onboardingService) {
    this.qualificationService = qualificationService;
    this.onboardingService = onboardingService;
  }

  @PostMapping("/qualifications")
  @PreAuthorize("hasAuthority('procurement:qualification:write')")
  public ApiResponse<Long> submit(@Valid @RequestBody SubmitQualificationRequest request) {
    return ApiResponse.success(
        qualificationService.submit(
            request.tenantId(),
            request.supplierId(),
            request.certType(),
            request.maskedCertNo(),
            request.expiresAt(),
            request.attachmentRef(),
            request.ocrResultJson()));
  }

  @GetMapping("/qualifications/expiring")
  @PreAuthorize("hasAuthority('procurement:qualification:read')")
  public ApiResponse<List<Qualification>> expiring(@Valid ExpiringQuery query) {
    return ApiResponse.success(qualificationService.expiring(query.tenantId()));
  }

  @PostMapping("/onboardings/{no}/approve")
  @PreAuthorize("hasAuthority('procurement:onboarding:approve')")
  public ApiResponse<Void> approve(
      @PathVariable("no") String no, @Valid @RequestBody ApproveOnboardingRequest request) {
    onboardingService.approveByHuman(request.tenantId(), no, request.operator());
    return ApiResponse.success(null);
  }

  public record SubmitQualificationRequest(
      Long tenantId,
      String supplierId,
      String certType,
      String maskedCertNo,
      Date expiresAt,
      String attachmentRef,
      String ocrResultJson) {}

  public record ExpiringQuery(Long tenantId) {}

  public record ApproveOnboardingRequest(Long tenantId, String operator) {}
}
