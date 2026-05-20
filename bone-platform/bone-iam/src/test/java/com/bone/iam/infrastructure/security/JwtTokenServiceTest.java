package com.bone.iam.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.iam.infrastructure.config.JwtConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecretKey("test-secret-key-at-least-32-bytes-long!!");
        config.setExpirationMs(3_600_000L);
        config.setTokenPrefix("Bearer ");
        config.setHeaderName("Authorization");
        jwtTokenService = new JwtTokenService(config);
    }

    @Test
    void generateTokenEmbedsScopesAndTenantId() {
        List<String> scopes = List.of("metadata:read", "extension:plugins:deploy");
        String token = jwtTokenService.generateToken(1L, "admin", 0L, scopes);

        var principal = jwtTokenService.parse("Bearer " + token);
        assertTrue(principal.isPresent());
        assertEquals("admin", principal.get().username());
        assertEquals("1", principal.get().userId());
        assertEquals("0", principal.get().tenantId());
        assertEquals(scopes, principal.get().scopes());
    }
}
