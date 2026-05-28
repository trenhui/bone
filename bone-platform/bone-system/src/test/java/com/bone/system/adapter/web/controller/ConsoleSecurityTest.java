package com.bone.system.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 控制台方法级 {@code @PreAuthorize('sys:console:read')} 集成测试（详设 §5.0）。
 *
 * <p>验证：未认证→401、缺权限→403、含权限→200。Token 使用与生产同样的 HS256 验签流程，
 * 仅密钥由 test profile 注入；不依赖 bone-iam 实例。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
class ConsoleSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${bone.iam.jwt.secret-key}")
    private String secretKey;

    @Test
    void overview_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/console/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overview_with_token_lacking_scope_returns_403() throws Exception {
        String token = issueToken(List.of("iam:accounts:read"));
        mockMvc.perform(get("/api/v1/console/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void overview_with_correct_scope_returns_200() throws Exception {
        String token = issueToken(List.of("sys:console:read"));
        mockMvc.perform(get("/api/v1/console/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void services_with_correct_scope_returns_200() throws Exception {
        String token = issueToken(List.of("sys:console:read"));
        mockMvc.perform(get("/api/v1/console/services").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void quick_actions_without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/console/quick-actions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuator_health_is_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private String issueToken(List<String> scopes) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("admin")
                .claim("userId", "1")
                .claim("tenantId", "0")
                .claim("scopes", scopes)
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(key)
                .compact();
    }
}
