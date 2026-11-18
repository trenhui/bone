package com.bone.procurement.fulfillment.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** ASN 创建命令。身份从安全上下文取，不接受客户端伪造 vendorPartyId。 */
public record AsnCommand(
    @NotNull Long tenantId,
    @NotBlank String asnNo,
    @NotBlank String poNo,
    String shipDate,
    String carrier,
    String trackingNo,
    List<String> sscc,
    List<Line> lines) {

  public record Line(
      Integer lineNo,
      Integer poLineNo,
      String itemId,
      BigDecimal qty,
      String uom,
      String packRef) {}
}
