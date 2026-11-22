package com.bone.procurement.qualification.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.qualification.application.OnboardingApplicationService;
import com.bone.procurement.qualification.application.QualificationApplicationService;
import com.bone.procurement.qualification.domain.gateway.ReviewIdentityPort;
import com.bone.procurement.qualification.domain.gateway.ReviewIdentityPort.ReviewIdentity;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
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
  private final ReviewIdentityPort reviewIdentity;

  public QualificationController(
      QualificationApplicationService qualificationService,
      OnboardingApplicationService onboardingService,
      ReviewIdentityPort reviewIdentity) {
    this.qualificationService = qualificationService;
    this.onboardingService = onboardingService;
    this.reviewIdentity = reviewIdentity;
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

  @GetMapping("/qualifications/{id}")
  @PreAuthorize("hasAuthority('procurement:qualification:read')")
  public ApiResponse<Qualification> findOne(@PathVariable("id") Long id, @Valid FindQuery query) {
    return ApiResponse.success(qualificationService.findOne(query.tenantId(), id));
  }

  @PostMapping("/qualifications/{id}/ocr-review")
  @PreAuthorize("hasAuthority('procurement:qualification:write')")
  public ApiResponse<Void> reviewOcr(
      @PathVariable("id") Long id, @Valid @RequestBody OcrReviewRequest request) {
    ReviewIdentity identity = reviewIdentity.current();
    if (identity == null) {
      throw new IllegalArgumentException("人工终决需要登录态身份与租户上下文，禁止缺省租户或匿名复核");
    }
    // 请求体自报的租户/复核人不可信：与登录态不一致即拒绝，一致才落登录态权威值。
    if (request.tenantId() != null && !request.tenantId().equals(identity.tenantId())) {
      throw new IllegalArgumentException("请求租户与登录态不一致，禁止跨租户复核");
    }
    if (request.reviewer() != null
        && !request.reviewer().isBlank()
        && !request.reviewer().trim().equals(identity.reviewer())) {
      throw new IllegalArgumentException("请求复核人与登录态不一致，禁止冒名复核");
    }
    qualificationService.reviewOcrByHuman(
        identity.tenantId(), id, identity.reviewer(), request.accurate(), new Date());
    return ApiResponse.success(null);
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

  public record OcrReviewRequest(Long tenantId, String reviewer, boolean accurate) {}

  public record FindQuery(Long tenantId) {}
}
