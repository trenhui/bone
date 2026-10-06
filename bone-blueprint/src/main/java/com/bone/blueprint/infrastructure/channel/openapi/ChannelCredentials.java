package com.bone.blueprint.infrastructure.channel.openapi;

import java.time.Instant;

/**
 * 渠道开放平台凭证（appKey / 密钥 / 授权令牌）。
 *
 * <p><b>为什么是一个 record 而不是直接把配置对象透传</b>：凭证来源可能不止一个（yml 配置、密钥管理、令牌刷新写回），
 * 透传配置对象会把「来源」与「值」耦在一起，刷新令牌后还要判断到底改了哪个来源。 收成值对象后，刷新就是「换一个值」，与来源无关。
 *
 * @param appKey 应用标识（淘宝 appKey / 京东 appKey / 拼多多 client_id）
 * @param appSecret 应用密钥（仅存在于内存，任何日志输出都必须先脱敏）
 * @param accessToken 授权令牌（令牌走 Header 的渠道另有 Header 承载，这里仅是签名入参）
 * @param expiresAt 令牌过期时刻（仅用于告警与刷新提示，不参与鉴权判断）
 */
public record ChannelCredentials(
    String appKey, String appSecret, String accessToken, Instant expiresAt) {

  public boolean hasSecret() {
    return appSecret != null && !appSecret.isBlank();
  }

  public boolean hasToken() {
    return accessToken != null && !accessToken.isBlank();
  }
}
