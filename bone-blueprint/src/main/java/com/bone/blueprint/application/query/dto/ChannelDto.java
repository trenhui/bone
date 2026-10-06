package com.bone.blueprint.application.query.dto;

import com.bone.blueprint.domain.model.channel.Channel;
import com.bone.blueprint.domain.model.channel.valueobject.ChannelCode;
import java.time.Instant;

/**
 * 渠道出参。
 *
 * @param extImplCode 命中的扩展实现 code（可观测字段；为空说明该渠道尚未被路由过）
 */
public record ChannelDto(
    String id,
    String channelCode,
    String channelName,
    String extImplCode,
    String apiEndpoint,
    Boolean enabled,
    Boolean orderSyncEnabled,
    Instant lastSyncAt,
    String remark,
    Instant createdAt,
    Instant updatedAt) {

  public static ChannelDto from(Channel channel) {
    if (channel == null) {
      return null;
    }
    ChannelCode code = ChannelCode.parseOrNull(channel.getChannelCode());
    return new ChannelDto(
        String.valueOf(channel.getId()),
        channel.getChannelCode(),
        channel.getChannelName() != null
            ? channel.getChannelName()
            : (code == null ? channel.getChannelCode() : code.displayName()),
        channel.getExtImplCode(),
        channel.getApiEndpoint(),
        channel.getEnabled(),
        channel.getOrderSyncEnabled(),
        channel.getLastSyncAt(),
        channel.getRemark(),
        channel.getCreatedAt(),
        channel.getUpdatedAt());
  }
}
