package com.bone.iam.infrastructure.security;

import com.bone.iam.application.port.out.TokenBlacklistPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link TokenBlacklistPort} 的 Redis 实现（未装配 Redis 时静默降级为「不拉黑」）。
 *
 * <p>落 {@code infrastructure/security}：技术能力端口按 E-13.3 落 {@code infrastructure/<具体能力>}。
 */
@Component
public class TokenBlacklistPortAdapter implements TokenBlacklistPort {

  private static final String KEY_PREFIX = "iam:token:blacklist:";

  private final StringRedisTemplate redisTemplate;

  public TokenBlacklistPortAdapter(@Autowired(required = false) StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void blacklist(String accessToken, Duration ttl) {
    if (redisTemplate == null || accessToken == null || accessToken.isBlank()) {
      return;
    }
    redisTemplate.opsForValue().set(KEY_PREFIX + accessToken, "1", ttl);
  }

  @Override
  public boolean isBlacklisted(String accessToken) {
    if (redisTemplate == null || accessToken == null || accessToken.isBlank()) {
      return false;
    }
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + accessToken));
  }
}
