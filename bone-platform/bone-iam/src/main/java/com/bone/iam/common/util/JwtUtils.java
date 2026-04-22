package com.bone.iam.common.util;

import com.bone.iam.infrastructure.config.JwtConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

public class JwtUtils {
    public static String generateToken(String userId, String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .setExpiration(new Date(System.currentTimeMillis() + JwtConfig.EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS512, JwtConfig.SECRET_KEY)
                .compact();
    }

    public static String getUserIdFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(JwtConfig.SECRET_KEY)
                .parseClaimsJws(token.replace(JwtConfig.TOKEN_PREFIX, ""))
                .getBody()
                .get("userId", String.class);
    }

    public static String getUsernameFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(JwtConfig.SECRET_KEY)
                .parseClaimsJws(token.replace(JwtConfig.TOKEN_PREFIX, ""))
                .getBody()
                .getSubject();
    }
}