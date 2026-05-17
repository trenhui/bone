package com.bone.engine.extension.studio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class StudioIdempotencyServiceTest {

    private StudioIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new StudioIdempotencyService(new ObjectMapper());
        MDC.put("tenantId", "1");
        MDC.put("userId", "u1");
    }

    @Test
    void replayReturnsSameResponseForSameFingerprint() {
        String key = "550e8400-e29b-41d4-a716-446655440000";
        String fp = StudioIdempotencyService.fingerprint("{\"name\":\"a\"}");
        ResponseEntity<ApiResponse<String>> original =
                ResponseEntity.status(HttpStatus.CREATED)
                        .header("Location", "/api/v1/extension/points/1")
                        .body(ApiResponse.success("ok", "data"));
        service.remember(key, "POST", "/api/v1/extension/points", fp, original);

        var replay = service.replay(key, "POST", "/api/v1/extension/points", fp);
        assertTrue(replay.isPresent());
        assertEquals(201, replay.get().getStatusCode().value());
        assertEquals("data", replay.get().getBody().getData());
    }

    @Test
    void differentBodyThrowsConflict() {
        String key = "550e8400-e29b-41d4-a716-446655440001";
        String fp1 = StudioIdempotencyService.fingerprint("a");
        service.remember(
                key,
                "POST",
                "/api/v1/extension/plugins/1:deploy",
                fp1,
                ResponseEntity.ok(ApiResponse.success("ok", "x")));

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.replay(key, "POST", "/api/v1/extension/plugins/1:deploy", "different"));
    }
}
