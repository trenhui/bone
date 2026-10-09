package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.replenishment.ReplenishmentOrder;
import java.time.Instant;

/** 补货单出参。 */
public record ReplenishmentOrderDto(
    String id,
    String replenishNo,
    String productId,
    String productName,
    String warehouseCode,
    Integer quantity,
    Integer suggestedQty,
    Integer availableSnapshot,
    Integer safetySnapshot,
    String supplierCode,
    String status,
    String remark,
    Instant submittedAt,
    Instant approvedAt,
    Instant receivedAt,
    Instant createdAt,
    Instant updatedAt) {

  public static ReplenishmentOrderDto from(ReplenishmentOrder entity) {
    if (entity == null) {
      return null;
    }
    return new ReplenishmentOrderDto(
        String.valueOf(entity.getId()),
        entity.getReplenishNo(),
        String.valueOf(entity.getProductId()),
        entity.getProductName(),
        entity.getWarehouseCode(),
        entity.getQuantity(),
        entity.getSuggestedQty(),
        entity.getAvailableSnapshot(),
        entity.getSafetySnapshot(),
        entity.getSupplierCode(),
        entity.getStatus() == null ? null : entity.getStatus().name(),
        entity.getRemark(),
        entity.getSubmittedAt(),
        entity.getApprovedAt(),
        entity.getReceivedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
