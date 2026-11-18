package com.bone.procurement.fulfillment.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** PO 确认命令。身份从安全上下文取，不接受客户端伪造 vendorPartyId。 */
public record AckCommand(
    @NotNull Long tenantId,
    @NotBlank String poNo,
    @NotNull Integer poVersion,
    @NotBlank String ackType,
    List<AckLine> lines) {

  public record AckLine(Integer lineNo, Integer acceptedQty, String reason) {}
}
