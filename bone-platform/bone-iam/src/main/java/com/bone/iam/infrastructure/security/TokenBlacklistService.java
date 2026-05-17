package com.bone.iam.infrastructure.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Access Token 黑名单（登出后直至原 JWT 过期）。
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "iam:token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String accessToken, Duration ttl) {
        if (redisTemplate == null || accessToken == null || accessToken.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + accessToken, "1", ttl);
    }

    public boolean isBlacklisted(String accessToken) {
        if (redisTemplate == null || accessToken == null || accessToken.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + accessToken));
    }
}
