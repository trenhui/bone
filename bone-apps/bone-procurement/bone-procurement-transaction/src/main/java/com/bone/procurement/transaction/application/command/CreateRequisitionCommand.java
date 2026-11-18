package com.bone.procurement.transaction.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 建请购单命令。 */
public record CreateRequisitionCommand(
    @NotNull Long tenantId,
    @NotNull Long orgId,
    @NotNull Long requesterId,
    @NotNull BigDecimal totalAmount,
    @NotBlank String currency,
    String budgetRef) {}
