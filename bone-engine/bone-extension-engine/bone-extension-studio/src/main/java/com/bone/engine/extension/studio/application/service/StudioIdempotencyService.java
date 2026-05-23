package com.bone.engine.extension.studio.application.service;

import com.bone.core.model.ApiResponse;
import com.bone.engine.extension.studio.common.exception.IdempotencyConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 进程内幂等缓存（[Target] Bone-API-规范 §8）；生产可替换为 Redis。
 */
@Service
public class StudioIdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final ObjectMapper objectMapper;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public StudioIdempotencyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static String fingerprint(String raw) {
        if (raw == null || raw.isBlank()) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    public String scopeKey(String idempotencyKey, String method, String path) {
        String tenant = nullToEmpty(MDC.get("tenantId"));
        String user = nullToEmpty(MDC.get("userId"));
        return tenant + "|" + user + "|" + idempotencyKey.trim() + "|" + method + "|" + path;
    }

    public boolean hasKey(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<ResponseEntity<ApiResponse<T>>> replay(
            String idempotencyKey, String method, String path, String requestFingerprint) {
        if (!hasKey(idempotencyKey)) {
            return Optional.empty();
        }
        purgeExpired();
        String key = scopeKey(idempotencyKey, method, path);
        Entry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException("Idempotency-Key 已用于不同请求体");
        }
        try {
            JsonNode snap = objectMapper.readTree(entry.snapshotJson());
            @SuppressWarnings("unchecked")
            ApiResponse<T> body =
                    objectMapper.convertValue(
                            snap.get("body"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructParametricType(ApiResponse.class, Object.class));
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(snap.get("status").asInt());
            if (snap.hasNonNull("location")) {
                builder.header(HttpHeaders.LOCATION, snap.get("location").asText());
            }
            return Optional.of(builder.body(body));
        } catch (JsonProcessingException ex) {
            cache.remove(key);
            return Optional.empty();
        }
    }

    public <T> void remember(
            String idempotencyKey,
            String method,
            String path,
            String requestFingerprint,
            ResponseEntity<ApiResponse<T>> response) {
        if (!hasKey(idempotencyKey) || response == null || response.getBody() == null) {
            return;
        }
        try {
            ObjectNode snap = objectMapper.createObjectNode();
            snap.put("status", response.getStatusCode().value());
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location != null) {
                snap.put("location", location);
            }
            snap.set("body", toJsonBody(response.getBody()));
            cache.put(
                    scopeKey(idempotencyKey, method, path),
                    new Entry(Instant.now(), requestFingerprint, objectMapper.writeValueAsString(snap)));
        } catch (JsonProcessingException ignored) {
            // skip cache on serialization failure
        }
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        cache.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }

  private ObjectNode toJsonBody(ApiResponse<?> body) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("success", body.getSuccess());
        node.put("code", body.getCode());
        node.put("message", body.getMessage());
        if (body.getTimestamp() != null) {
            node.put("timestamp", body.getTimestamp());
        }
        node.set("data", objectMapper.valueToTree(body.getData()));
        return node;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Entry(Instant createdAt, String requestFingerprint, String snapshotJson) {}
}
