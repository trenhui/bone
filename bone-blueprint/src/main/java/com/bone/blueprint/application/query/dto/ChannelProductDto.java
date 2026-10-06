package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.channel.ChannelProduct;
import java.math.BigDecimal;
import java.time.Instant;

/** 渠道商品出参。 */
public record ChannelProductDto(
    String id,
    String channelCode,
    String productId,
    String productName,
    String channelProductId,
    String listingStatus,
    BigDecimal listingPrice,
    Integer listingStock,
    Instant lastSyncAt,
    String failReason,
    Instant createdAt,
    Instant updatedAt) {

  public static ChannelProductDto from(ChannelProduct entity) {
    if (entity == null) {
      return null;
    }
    return new ChannelProductDto(
        String.valueOf(entity.getId()),
        entity.getChannelCode(),
        String.valueOf(entity.getProductId()),
        entity.getProductName(),
        entity.getChannelProductId(),
        entity.getListingStatus() == null ? null : entity.getListingStatus().name(),
        entity.getListingPrice(),
        entity.getListingStock(),
        entity.getLastSyncAt(),
        entity.getFailReason(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
