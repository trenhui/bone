package com.bone.blueprint.domain.extension.channel;

/** 渠道发货回传结果。 */
public record ChannelShipmentResult(boolean success, String errorCode, String message) {

  public static ChannelShipmentResult ok(String message) {
    return new ChannelShipmentResult(true, null, message);
  }

  public static ChannelShipmentResult fail(String errorCode, String message) {
    return new ChannelShipmentResult(false, errorCode, message);
  }
}
