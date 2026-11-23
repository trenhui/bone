package com.bone.procurement.sourcing.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.sourcing.application.SourcingApplicationService;
import com.bone.procurement.sourcing.domain.gateway.AwardIdentityPort;
import com.bone.procurement.sourcing.domain.gateway.AwardIdentityPort.AwardIdentity;
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
  private final AwardIdentityPort awardIdentity;

  public SourcingController(SourcingApplicationService service, AwardIdentityPort awardIdentity) {
    this.service = service;
    this.awardIdentity = awardIdentity;
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

  /** 人工授标：确认人只取登录态，请求体字段仅做一致性校验，不参与赋值。 */
  @PostMapping("/rfx/{rfxNo}/award")
  @PreAuthorize("hasAuthority('procurement:rfx:approve')")
  public ApiResponse<Void> awardRfx(
      @PathVariable("rfxNo") String rfxNo, @Valid @RequestBody AwardRfxRequest request) {
    AwardIdentity identity = awardIdentity.current();
    if (identity == null
        || identity.tenantId() == null
        || identity.operator() == null
        || identity.operator().isBlank()) {
      throw new IllegalArgumentException("人工授标需要登录态身份与租户上下文，禁止匿名终决");
    }
    if (request.tenantId() != null && !request.tenantId().equals(identity.tenantId())) {
      throw new IllegalArgumentException("请求租户与登录态不一致，禁止跨租户授标");
    }
    if (request.confirmedBy() != null
        && !request.confirmedBy().isBlank()
        && !request.confirmedBy().trim().equals(identity.operator())) {
      throw new IllegalArgumentException("请求确认人与登录态不一致，禁止冒名授标");
    }
    service.awardByRfxNo(identity.tenantId(), rfxNo, identity.operator());
    return ApiResponse.success(null);
  }

  public record AwardRfxRequest(Long tenantId, String confirmedBy) {}

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
