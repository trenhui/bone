package com.bone.procurement.transaction.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** 建采购订单命令。 */
public record CreatePurchaseOrderCommand(
    @NotNull Long tenantId,
    @NotNull Long orgId,
    @NotBlank String vendorPartyId,
    @NotNull Long legalEntityId,
    /** 客户端声明总额；非空时必须与逐行舍入后的行合计一致（M4-5 探针）。 */
    BigDecimal declaredAmount,
    BigDecimal fxRate,
    String fxAsOf,
    String fxSource,
    String taxCodeVersion,
    String budgetRef,
    @NotEmpty @Valid List<Line> lines) {

  public record Line(
      @NotBlank String itemId,
      String itemName,
      @NotNull BigDecimal quantity,
      String unit,
      @NotNull BigDecimal netPrice,
      @NotNull BigDecimal taxRate) {}
}
