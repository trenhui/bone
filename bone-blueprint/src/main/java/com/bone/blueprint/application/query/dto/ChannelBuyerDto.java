package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.channelbuyer.BindingSource;
import com.bone.blueprint.domain.model.channelbuyer.ChannelBuyer;
import java.time.Instant;

/**
 * 渠道买家映射出参。
 *
 * <p><b>ID 一律以字符串出参</b>：后端全局把 {@code Long} 序列化为字符串（雪花ID 超过 2^53，前端 JS Number 会丢精度）。
 *
 * @param customerId 内部客户ID；「0」表示尚未绑定（影子映射），前端据此显示「待绑定」标签而不是当成真实客户
 * @param bound 是否已绑定到真实内部客户
 * @param bindingSource 绑定来源：MANUAL / AUTO_SHADOW
 * @param bindingSourceName 绑定来源中文名（前端直接展示，避免各页面各写一份翻译）
 */
public record ChannelBuyerDto(
    String id,
    String channelCode,
    String channelBuyerId,
    String channelBuyerNick,
    String customerId,
    String customerName,
    boolean bound,
    String bindingSource,
    String bindingSourceName,
    Integer orderCount,
    Instant firstSeenAt,
    Instant lastOrderAt,
    String remark,
    Instant createdAt,
    Instant updatedAt) {

  public static ChannelBuyerDto from(ChannelBuyer buyer) {
    if (buyer == null) {
      return null;
    }
    return new ChannelBuyerDto(
        String.valueOf(buyer.getId()),
        buyer.getChannelCode(),
        buyer.getChannelBuyerId(),
        buyer.getChannelBuyerNick(),
        String.valueOf(
            buyer.getCustomerId() == null
                ? ChannelBuyer.UNBOUND_CUSTOMER_ID
                : buyer.getCustomerId()),
        buyer.getCustomerName(),
        buyer.isBound(),
        buyer.getBindingSource(),
        displaySource(buyer.getBindingSource()),
        buyer.getOrderCount(),
        buyer.getFirstSeenAt(),
        buyer.getLastOrderAt(),
        buyer.getRemark(),
        buyer.getCreatedAt(),
        buyer.getUpdatedAt());
  }

  private static String displaySource(String source) {
    if (BindingSource.MANUAL.name().equals(source)) {
      return "人工绑定";
    }
    if (BindingSource.AUTO_SHADOW.name().equals(source)) {
      return "自动影子（待绑定）";
    }
    return source == null ? "" : source;
  }
}
