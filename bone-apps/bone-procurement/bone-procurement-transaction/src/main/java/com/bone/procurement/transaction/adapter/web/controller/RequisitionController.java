package com.bone.procurement.transaction.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.procurement.transaction.application.RequisitionApplicationService;
import com.bone.procurement.transaction.application.command.CreateRequisitionCommand;
import com.bone.procurement.transaction.domain.model.requisition.Requisition;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 请购单 HTTP 端点（能力开关关闭时不注册）。 */
@RestController
@RequestMapping("/api/v1/procurement/requisitions")
@ConditionalOnProperty(
    prefix = "bone.procurement.transaction",
    name = "enabled",
    havingValue = "true")
public class RequisitionController {

  private final RequisitionApplicationService service;

  public RequisitionController(RequisitionApplicationService service) {
    this.service = service;
  }

  @PostMapping("")
  @PreAuthorize("hasAuthority('procurement:requisition:write')")
  public ApiResponse<Requisition> create(@Valid @RequestBody CreateRequisitionCommand cmd) {
    return ApiResponse.success(service.create(cmd));
  }

  @PostMapping("/{no}/convert")
  @PreAuthorize("hasAuthority('procurement:requisition:write')")
  public ApiResponse<Requisition> convert(
      @PathVariable("no") String no, @RequestBody ConvertRequest request) {
    return ApiResponse.success(
        service.convert(request.tenantId(), no, request.vendorPartyId(), request.legalEntityId()));
  }

  public record ConvertRequest(Long tenantId, String vendorPartyId, Long legalEntityId) {}
}
