package com.bone.blueprint.infrastructure.channel.openapi;

/**
 * 渠道网关 HTTP 响应。
 *
 * @param statusCode HTTP 状态码
 * @param body 响应体（渠道协议决定内容形态，可能是 JSON、JSON-in-String，或错误页 HTML）
 */
public record ChannelHttpResponse(int statusCode, String body) {

  public boolean isOk() {
    return statusCode >= 200 && statusCode < 300;
  }
}
