package com.bone.procurement.fulfillment.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** 发票提交命令。身份从安全上下文取，不接受客户端伪造 vendorPartyId。 */
public record InvoiceCommand(
    @NotNull Long tenantId,
    @NotBlank String invoiceNo,
    String poNo,
    @NotNull BigDecimal amount,
    BigDecimal tax,
    String currency,
    @NotBlank String category,
    List<InvoiceLine> lines) {

  public record InvoiceLine(String itemNo, BigDecimal qty, BigDecimal price, BigDecimal amount) {}
}
