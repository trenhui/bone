package com.bone.metadata.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类：生成与解析验证
 */
@Component
public class JwtUtil {
    /** 生产环境请将密钥存储在安全配置中心，而非硬编码 */
    private Key key;
    private final long expirationMillis = 1000L * 60 * 60; // 1 小时

    @PostConstruct
    public void init() {
        key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    }

    /**
     * 生成 JWT
     * @param subject 用户标识（用户名或用户ID）
     * @param claims  自定义载荷（如 roles）
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验证 JWT，若无效或过期则抛出异常
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
