package com.bone.procurement.transaction.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.procurement.transaction.application.PurchaseOrderApplicationService;
import com.bone.procurement.transaction.application.command.CreatePurchaseOrderCommand;
import com.bone.procurement.transaction.domain.model.po.PurchaseOrder;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 采购订单 HTTP 端点（能力开关关闭时不注册）。 */
@RestController
@RequestMapping("/api/v1/procurement/purchase-orders")
@ConditionalOnProperty(
    prefix = "bone.procurement.transaction",
    name = "enabled",
    havingValue = "true")
public class PurchaseOrderController {

  private final PurchaseOrderApplicationService service;

  public PurchaseOrderController(PurchaseOrderApplicationService service) {
    this.service = service;
  }

  @PostMapping("")
  @PreAuthorize("hasAuthority('procurement:purchase-order:write')")
  public ApiResponse<PurchaseOrder> create(@Valid @RequestBody CreatePurchaseOrderCommand cmd) {
    return ApiResponse.success(service.create(cmd));
  }

  @GetMapping({"", "/"})
  @PreAuthorize("hasAuthority('procurement:purchase-order:read')")
  public ApiResponse<PageResult<PurchaseOrder>> list(
      @RequestParam("tenantId") Long tenantId,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(service.list(tenantId, status, page, size));
  }

  @PostMapping("/{no}/submit")
  @PreAuthorize("hasAuthority('procurement:purchase-order:write')")
  public ApiResponse<PurchaseOrder> submit(
      @PathVariable("no") String no, @RequestParam("tenantId") Long tenantId) {
    return ApiResponse.success(service.transition(tenantId, no, "SUBMIT"));
  }

  @PostMapping("/{no}/send")
  @PreAuthorize("hasAuthority('procurement:purchase-order:write')")
  public ApiResponse<PurchaseOrder> send(
      @PathVariable("no") String no, @RequestParam("tenantId") Long tenantId) {
    return ApiResponse.success(service.send(tenantId, no));
  }

  @GetMapping("/{no}")
  @PreAuthorize("hasAuthority('procurement:purchase-order:read')")
  public ApiResponse<PurchaseOrder> detail(
      @PathVariable("no") String no, @RequestParam("tenantId") Long tenantId) {
    PurchaseOrder po =
        service
            .findByNo(tenantId, no)
            .orElseThrow(() -> new IllegalArgumentException("采购订单不存在：" + no));
    return ApiResponse.success(po);
  }
}
