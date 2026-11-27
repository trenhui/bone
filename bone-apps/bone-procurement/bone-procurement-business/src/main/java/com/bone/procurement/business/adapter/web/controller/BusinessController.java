package com.bone.procurement.business.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.business.application.BusinessApplicationService;
import com.bone.procurement.business.domain.gateway.BusinessIdentityPort;
import com.bone.procurement.business.domain.model.business.QuotationComparison;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 业务聚合 HTTP 端点；身份只取登录态，请求体字段仅做一致性校验。 */
@RestController
@RequestMapping("/api/v1/procurement/business")
public class BusinessController {

  private final BusinessApplicationService service;
  private final BusinessIdentityPort identity;

  public BusinessController(BusinessApplicationService service, BusinessIdentityPort identity) {
    this.service = service;
    this.identity = identity;
  }

  @GetMapping("/{rfxNo}/award")
  @PreAuthorize("hasAuthority('procurement:qualification:read')")
  public ApiResponse<AwardView> findAward(
      @PathVariable("rfxNo") String rfxNo,
      @RequestParam(name = "tenantId", required = false) Long tenantId) {
    return service
        .findAward(tenantId, rfxNo)
        .map(BusinessController::view)
        .map(ApiResponse::success)
        .orElseGet(() -> ApiResponse.success(null));
  }

  @PostMapping("/{rfxNo}/award")
  @PreAuthorize("hasAuthority('procurement:rfx:approve')")
  public ApiResponse<AwardView> award(
      @PathVariable("rfxNo") String rfxNo, @Valid @RequestBody AwardRequest request) {
    BusinessIdentityPort.Actor actor = requireActor();
    requireTenant(request.tenantId(), actor);
    if (request.confirmedBy() != null
        && !request.confirmedBy().isBlank()
        && !request.confirmedBy().trim().equals(actor.operator())) {
      throw new IllegalArgumentException("请求确认人与登录态不一致，禁止冒名授标");
    }
    return ApiResponse.success(
        view(
            service.award(
                actor.tenantId(), rfxNo, request.supplierId(), request.reason(), new Date())));
  }

  @PostMapping("/bids")
  @PreAuthorize("hasAuthority('procurement:sourcing-project:write')")
  public ApiResponse<Void> submitBid(@Valid @RequestBody BidRequest request) {
    service.submitBid(
        request.tenantId(),
        request.bidId(),
        request.rfxNo(),
        request.supplierId(),
        request.sourceRef(),
        request.dueAt(),
        new Date());
    return ApiResponse.success(null);
  }

  @PostMapping("/quotations")
  @PreAuthorize("hasAuthority('procurement:sourcing-project:write')")
  public ApiResponse<Void> recordQuotation(@Valid @RequestBody QuotationRequest request) {
    service.recordQuotation(
        request.tenantId(),
        request.quoteId(),
        request.rfxNo(),
        request.supplierId(),
        request.amount(),
        request.currency(),
        request.validUntil(),
        request.aiScore(),
        request.aiRecommendation());
    return ApiResponse.success(null);
  }

  @PostMapping("/capa")
  @PreAuthorize("hasAuthority('procurement:qualification:write')")
  public ApiResponse<Void> createCapa(@Valid @RequestBody CapaRequest request) {
    service.createCapa(
        request.tenantId(),
        request.capaNo(),
        request.supplierId(),
        request.issue(),
        request.owner(),
        request.dueAt());
    return ApiResponse.success(null);
  }

  @PostMapping("/capa/{capaNo}/close")
  @PreAuthorize("hasAuthority('procurement:qualification:write')")
  public ApiResponse<Void> closeCapa(
      @PathVariable("capaNo") String capaNo, @Valid @RequestBody CloseCapaRequest request) {
    service.closeCapa(request.tenantId(), capaNo, request.evidence(), new Date());
    return ApiResponse.success(null);
  }

  @GetMapping("/{rfxNo}/quotations")
  @PreAuthorize("hasAuthority('procurement:qualification:read')")
  public ApiResponse<List<QuotationComparison>> quotations(
      @PathVariable("rfxNo") String rfxNo,
      @RequestParam(name = "tenantId", required = false) Long tenantId,
      @RequestParam(name = "budgetCurrency") String budgetCurrency) {
    return ApiResponse.success(
        service.compareQuotations(tenantId, rfxNo, budgetCurrency, new Date()));
  }

  private BusinessIdentityPort.Actor requireActor() {
    BusinessIdentityPort.Actor actor = identity.current();
    if (actor == null
        || actor.tenantId() == null
        || actor.operator() == null
        || actor.operator().isBlank()) {
      throw new IllegalArgumentException("人工操作需要登录态身份与租户上下文");
    }
    return actor;
  }

  private static void requireTenant(Long requested, BusinessIdentityPort.Actor actor) {
    if (requested != null && !requested.equals(actor.tenantId())) {
      throw new IllegalArgumentException("请求租户与登录态不一致，禁止跨租户操作");
    }
  }

  private static AwardView view(com.bone.procurement.business.domain.model.business.Award award) {
    return new AwardView(
        award.tenantId(),
        award.rfxNo(),
        award.supplierId(),
        award.confirmedBy(),
        award.confirmedAt(),
        award.reason());
  }

  public record BidRequest(
      Long tenantId, String bidId, String rfxNo, String supplierId, String sourceRef, Date dueAt) {}

  public record QuotationRequest(
      Long tenantId,
      String quoteId,
      String rfxNo,
      String supplierId,
      BigDecimal amount,
      String currency,
      Date validUntil,
      BigDecimal aiScore,
      String aiRecommendation) {}

  public record CapaRequest(
      Long tenantId, String capaNo, String supplierId, String issue, String owner, Date dueAt) {}

  public record CloseCapaRequest(Long tenantId, String evidence) {}

  public record AwardRequest(Long tenantId, String supplierId, String reason, String confirmedBy) {}

  public record AwardView(
      Long tenantId,
      String rfxNo,
      String supplierId,
      String confirmedBy,
      Date confirmedAt,
      String reason) {}
}
