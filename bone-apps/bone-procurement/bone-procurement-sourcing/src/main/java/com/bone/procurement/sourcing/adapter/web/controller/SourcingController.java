package com.bone.procurement.sourcing.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.sourcing.application.SourcingApplicationService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Date;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 寻源 HTTP 端点（对齐 openapi.yaml §02 卷占位路径）。 */
@RestController
@RequestMapping("/api/v1/procurement")
public class SourcingController {

  private final SourcingApplicationService service;

  public SourcingController(SourcingApplicationService service) {
    this.service = service;
  }

  @PostMapping("/sourcing-projects")
  @PreAuthorize("hasAuthority('procurement:sourcing-project:write')")
  public ApiResponse<Long> createProject(@Valid @RequestBody CreateSourcingProjectRequest request) {
    return ApiResponse.success(
        service.createProject(
            request.tenantId(),
            request.projectNo(),
            request.categoryId(),
            request.budgetAmount(),
            request.currency(),
            request.strategy(),
            request.ownerId(),
            request.rfxNo(),
            request.rfxType(),
            request.dueAt()));
  }

  @PostMapping("/rfx/{rfxNo}/publish")
  @PreAuthorize("hasAuthority('procurement:rfx:approve')")
  public ApiResponse<Void> publishRfx(
      @PathVariable("rfxNo") String rfxNo, @Valid @RequestBody PublishRfxRequest request) {
    service.publishRfx(request.tenantId(), rfxNo);
    return ApiResponse.success(null);
  }

  public record CreateSourcingProjectRequest(
      Long tenantId,
      String projectNo,
      String categoryId,
      BigDecimal budgetAmount,
      String currency,
      String strategy,
      String ownerId,
      String rfxNo,
      String rfxType,
      Date dueAt) {}

  public record PublishRfxRequest(Long tenantId) {}
}
