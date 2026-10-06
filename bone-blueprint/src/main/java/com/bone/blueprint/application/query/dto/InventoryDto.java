package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.inventory.Inventory;
import java.time.Instant;

/** 库存出参。 */
public record InventoryDto(
    String id,
    String productId,
    String productName,
    String warehouseCode,
    Integer availableQty,
    Integer reservedQty,
    Integer safetyStock,
    Boolean lowStock,
    Instant createdAt,
    Instant updatedAt) {

  public static InventoryDto from(Inventory entity) {
    if (entity == null) {
      return null;
    }
    return new InventoryDto(
        String.valueOf(entity.getId()),
        String.valueOf(entity.getProductId()),
        entity.getProductName(),
        entity.getWarehouseCode(),
        entity.getAvailableQty(),
        entity.getReservedQty(),
        entity.getSafetyStock(),
        entity.isBelowSafetyStock(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
