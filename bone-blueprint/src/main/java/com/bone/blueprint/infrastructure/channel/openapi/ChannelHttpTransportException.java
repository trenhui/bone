package com.bone.blueprint.infrastructure.channel.openapi;

/**
 * 渠道网关网络层异常（超时 / 建连失败 / 响应体不可读）。
 *
 * <p><b>为什么与「渠道业务拒绝」分开</b>：网络异常意味着「我们没问到，渠道也不知道」，重试通常有效； 渠道业务拒绝意味着「问了、渠道明确拒绝」， 重试只会重复被拒。二者混成一个
 * {@code success=false} 后，重试策略只能一律重试或一律不重试—— 前者放大渠道压力，后者把瞬时抖动变成永久失败。
 */
public class ChannelHttpTransportException extends RuntimeException {

  public ChannelHttpTransportException(String message, Throwable cause) {
    super(message, cause);
  }
}
