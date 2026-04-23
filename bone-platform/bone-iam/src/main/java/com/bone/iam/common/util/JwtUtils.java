package com.bone.iam.common.util;

import com.bone.iam.infrastructure.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

public class JwtUtils {

    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JwtConfig.SECRET_KEY.getBytes());

    public static String generateToken(String userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .expiration(new Date(System.currentTimeMillis() + JwtConfig.EXPIRATION_TIME))
                .signWith(SIGNING_KEY)
                .compact();
    }

    public static String getUserIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(SIGNING_KEY)
                .build()
                .parseSignedClaims(token.replace(JwtConfig.TOKEN_PREFIX, ""))
                .getPayload()
                .get("userId", String.class);
    }

    public static String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(SIGNING_KEY)
                .build()
                .parseSignedClaims(token.replace(JwtConfig.TOKEN_PREFIX, ""))
                .getPayload()
                .getSubject();
    }
}