package com.bone.iam.infrastructure.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {
    public static final String SECRET_KEY = "your-secret-key-here";
    public static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000; // 2小时
    public static final long REFRESH_EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000; // 7天
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String HEADER_STRING = "Authorization";
}