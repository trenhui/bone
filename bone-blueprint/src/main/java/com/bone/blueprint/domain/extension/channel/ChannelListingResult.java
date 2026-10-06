package com.bone.blueprint.domain.extension.channel;

/**
 * 渠道商品操作结果（上架 / 下架 / 库存同步统一出参）。
 *
 * <p><b>为何不用抛异常表达失败</b>：渠道接口失败是<strong>预期内</strong>的（类目不符、资质过期、
 * 限流），不是编程错误。用异常会让「渠道拒绝」与「系统故障」混为一谈，污染 5xx 告警。 统一用 {@code success} + {@code errorCode} + {@code
 * message} 承载，由应用服务决定 是把实体置为 FAILED（业务失败）还是抛 5xx（系统故障）。
 *
 * @param success 是否成功
 * @param channelProductId 渠道侧商品ID（上架成功时必填）
 * @param errorCode 渠道错误码（失败时填，便于按码聚合告警）
 * @param message 可读说明（渠道原始 message 兜底）
 */
public record ChannelListingResult(
    boolean success, String channelProductId, String errorCode, String message) {

  public static ChannelListingResult ok(String channelProductId, String message) {
    return new ChannelListingResult(true, channelProductId, null, message);
  }

  public static ChannelListingResult fail(String errorCode, String message) {
    return new ChannelListingResult(false, null, errorCode, message);
  }
}
