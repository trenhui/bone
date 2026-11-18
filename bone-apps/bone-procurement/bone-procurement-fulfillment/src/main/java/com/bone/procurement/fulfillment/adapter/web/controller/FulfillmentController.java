package com.bone.procurement.fulfillment.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.procurement.fulfillment.application.AsnApplicationService;
import com.bone.procurement.fulfillment.application.FulfillmentQueryService;
import com.bone.procurement.fulfillment.application.InvoiceSubmissionApplicationService;
import com.bone.procurement.fulfillment.application.IrExceptionService;
import com.bone.procurement.fulfillment.application.PoAckApplicationService;
import com.bone.procurement.fulfillment.application.command.AckCommand;
import com.bone.procurement.fulfillment.application.command.AsnCommand;
import com.bone.procurement.fulfillment.application.command.InvoiceCommand;
import com.bone.procurement.fulfillment.domain.model.asn.Asn;
import com.bone.procurement.fulfillment.domain.model.invoice.InvoiceSubmission;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 履约内部端点（能力开关关闭时不注册）。路径 /api/v1/procurement/*（内部实现，非 LLD 门户 BFF 路径）。 */
@RestController
@RequestMapping("/api/v1/procurement")
@ConditionalOnProperty(
    prefix = "bone.procurement.fulfillment",
    name = "enabled",
    havingValue = "true")
public class FulfillmentController {

  private final PoAckApplicationService ackService;
  private final AsnApplicationService asnService;
  private final InvoiceSubmissionApplicationService invoiceService;
  private final IrExceptionService exceptionService;
  private final FulfillmentQueryService queryService;

  public FulfillmentController(
      PoAckApplicationService ackService,
      AsnApplicationService asnService,
      InvoiceSubmissionApplicationService invoiceService,
      IrExceptionService exceptionService,
      FulfillmentQueryService queryService) {
    this.ackService = ackService;
    this.asnService = asnService;
    this.invoiceService = invoiceService;
    this.exceptionService = exceptionService;
    this.queryService = queryService;
  }

  @PostMapping("/purchase-orders/{no}/ack")
  @PreAuthorize("hasAuthority('procurement:fulfillment:write')")
  public ApiResponse<?> ack(@PathVariable("no") String no, @Valid @RequestBody AckCommand cmd) {
    return ApiResponse.success(ackService.ack(withPoNo(cmd, no)));
  }

  @PostMapping("/asns")
  @PreAuthorize("hasAuthority('procurement:fulfillment:write')")
  public ApiResponse<Asn> createAsn(@Valid @RequestBody AsnCommand cmd) {
    return ApiResponse.success(asnService.create(cmd));
  }

  @PostMapping("/invoices")
  @PreAuthorize("hasAuthority('procurement:fulfillment:write')")
  public ApiResponse<InvoiceSubmission> submitInvoice(@Valid @RequestBody InvoiceCommand cmd) {
    return ApiResponse.success(invoiceService.submit(cmd));
  }

  @PostMapping("/ir-exceptions/{no}/supplement")
  @PreAuthorize("hasAuthority('procurement:fulfillment:write')")
  public ApiResponse<?> supplement(
      @PathVariable("no") String no,
      @RequestParam("tenantId") Long tenantId,
      @RequestParam("supplementRef") String supplementRef) {
    return ApiResponse.success(exceptionService.supplement(tenantId, no, supplementRef));
  }

  @GetMapping("/purchase-orders/{no}/fulfillment")
  @PreAuthorize("hasAuthority('procurement:fulfillment:read')")
  public ApiResponse<Map<String, Object>> fulfillmentView(
      @PathVariable("no") String no, @RequestParam("tenantId") Long tenantId) {
    return ApiResponse.success(queryService.view(tenantId, no));
  }

  @GetMapping("/ir-exceptions")
  @PreAuthorize("hasAuthority('procurement:fulfillment:read')")
  public ApiResponse<PageResult<?>> listExceptions(
      @RequestParam("tenantId") Long tenantId,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(exceptionService.list(tenantId, page, size));
  }

  private AckCommand withPoNo(AckCommand cmd, String no) {
    return new AckCommand(cmd.tenantId(), no, cmd.poVersion(), cmd.ackType(), cmd.lines());
  }
}
