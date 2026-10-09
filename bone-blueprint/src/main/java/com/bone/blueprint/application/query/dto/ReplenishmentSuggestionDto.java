package com.bone.blueprint.application.query.dto;

/** 低于安全库存的补货建议（只读投影）。 */
public record ReplenishmentSuggestionDto(
    String productId,
    String productName,
    String warehouseCode,
    Integer availableQty,
    Integer safetyStock,
    Integer suggestedQty,
    Boolean hasOpenOrder) {}
