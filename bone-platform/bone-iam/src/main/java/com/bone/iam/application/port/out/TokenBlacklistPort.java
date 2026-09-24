package com.bone.iam.application.port.out;

import java.time.Duration;

/**
 * 访问令牌黑名单端口（登出后至原 JWT 过期前生效）。
 *
 * <p>/*
 *
 * <p>按 E-10.2「应用流程需要通知、时钟、文件、幂等等技术能力 → {@code application/port/out}」放置：黑名单是 token
 * 状态的缓存能力，不是以本上下文语言声明的外部业务 ACL，故不属 {@code domain/gateway}。
 */
public interface TokenBlacklistPort {

  /** 将访问令牌加入黑名单，{@code ttl} 为剩余有效期。 */
  void blacklist(String accessToken, Duration ttl);

  /** 令牌是否已被拉黑（登出/踢出）。 */
  boolean isBlacklisted(String accessToken);
}
