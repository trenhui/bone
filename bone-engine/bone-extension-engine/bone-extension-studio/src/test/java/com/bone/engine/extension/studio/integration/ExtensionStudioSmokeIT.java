package com.bone.engine.extension.studio.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 进程内 E2E 冒烟：完整 Spring 上下文 + 随机端口 HTTP。
 */
@SpringBootTest(
        classes = ExtensionStudioApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("in-memory")
class ExtensionStudioSmokeIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void overviewAndPointsList() {
        String base = "http://localhost:" + port + "/api/v1/extension";
        ResponseEntity<Map<String, Object>> overview = restTemplate.exchange(
                base + "/overview",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, overview.getStatusCode());
        assertNotNull(overview.getBody());
        assertEquals(Boolean.TRUE, overview.getBody().get("success"));

        ResponseEntity<Map<String, Object>> points = restTemplate.exchange(
                base + "/points",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        assertEquals(HttpStatus.OK, points.getStatusCode());
        assertTrue(points.getBody().get("success") instanceof Boolean);
    }
}
