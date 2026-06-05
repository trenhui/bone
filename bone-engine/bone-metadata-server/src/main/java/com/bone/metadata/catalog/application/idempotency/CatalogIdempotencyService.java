package com.bone.metadata.catalog.application.idempotency;

import com.bone.core.result.ApiResponse;
import com.bone.metadata.catalog.common.exception.CatalogIdempotencyConflictException;
import com.bone.metadata.catalog.domain.gateway.CatalogIdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 元数据 catalog / EAV 写操作幂等（对齐 Stripe Idempotency-Key，默认进程内 24h）。 */
@Service
public class CatalogIdempotencyService {

  private static final Duration TTL = Duration.ofHours(24);

  private final ObjectMapper objectMapper;
  private final CatalogIdempotencyStore store;

  public CatalogIdempotencyService(ObjectMapper objectMapper, CatalogIdempotencyStore store) {
    this.objectMapper =
        objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.store = store;
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

  public <T> Optional<ResponseEntity<ApiResponse<T>>> replay(
      String idempotencyKey,
      String method,
      String path,
      String requestFingerprint,
      JavaType apiResponseType) {
    if (!hasKey(idempotencyKey)) {
      return Optional.empty();
    }
    String key = scopeKey(idempotencyKey, method, path);
    Optional<CatalogIdempotencyStore.Snapshot> snapshot = store.find(key);
    if (snapshot.isEmpty()) {
      return Optional.empty();
    }
    CatalogIdempotencyStore.Snapshot entry = snapshot.get();
    if (!entry.requestFingerprint().equals(requestFingerprint)) {
      throw new CatalogIdempotencyConflictException("Idempotency-Key 已用于不同请求体");
    }
    try {
      JsonNode snap = objectMapper.readTree(entry.snapshotJson());
      ApiResponse<T> body = objectMapper.convertValue(snap.get("body"), apiResponseType);
      ResponseEntity.BodyBuilder builder = ResponseEntity.status(snap.get("status").asInt());
      if (snap.hasNonNull("location")) {
        builder.header(HttpHeaders.LOCATION, snap.get("location").asText());
      }
      return Optional.of(builder.body(body));
    } catch (JsonProcessingException ex) {
      return Optional.empty();
    }
  }

  /** 记录 {@link ApiResponse} 信封类响应（catalog / runtime）。 */
  public <T> void rememberApiResponse(
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
      snap.set("body", objectMapper.valueToTree(response.getBody()));
      store.put(
          scopeKey(idempotencyKey, method, path),
          new CatalogIdempotencyStore.Snapshot(
              requestFingerprint, objectMapper.writeValueAsString(snap)),
          TTL);
    } catch (JsonProcessingException ignored) {
      // skip cache on serialization failure
    }
  }

  /** 记录裸 body 响应（EAV allocate 等）。 */
  public <T> void rememberRawBody(
      String idempotencyKey,
      String method,
      String path,
      String requestFingerprint,
      ResponseEntity<T> response) {
    if (!hasKey(idempotencyKey) || response == null) {
      return;
    }
    try {
      ObjectNode snap = objectMapper.createObjectNode();
      snap.put("status", response.getStatusCode().value());
      String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
      if (location != null) {
        snap.put("location", location);
      }
      snap.set("body", objectMapper.valueToTree(response.getBody()));
      store.put(
          scopeKey(idempotencyKey, method, path),
          new CatalogIdempotencyStore.Snapshot(
              requestFingerprint, objectMapper.writeValueAsString(snap)),
          TTL);
    } catch (JsonProcessingException ignored) {
      // skip
    }
  }

  public <T> Optional<ResponseEntity<T>> replayRawBody(
      String idempotencyKey,
      String method,
      String path,
      String requestFingerprint,
      JavaType bodyType) {
    if (!hasKey(idempotencyKey)) {
      return Optional.empty();
    }
    String key = scopeKey(idempotencyKey, method, path);
    Optional<CatalogIdempotencyStore.Snapshot> snapshot = store.find(key);
    if (snapshot.isEmpty()) {
      return Optional.empty();
    }
    CatalogIdempotencyStore.Snapshot entry = snapshot.get();
    if (!entry.requestFingerprint().equals(requestFingerprint)) {
      throw new CatalogIdempotencyConflictException("Idempotency-Key 已用于不同请求体");
    }
    try {
      JsonNode snap = objectMapper.readTree(entry.snapshotJson());
      T body = objectMapper.convertValue(snap.get("body"), bodyType);
      ResponseEntity.BodyBuilder builder = ResponseEntity.status(snap.get("status").asInt());
      if (snap.hasNonNull("location")) {
        builder.header(HttpHeaders.LOCATION, snap.get("location").asText());
      }
      return Optional.of(builder.body(body));
    } catch (JsonProcessingException ex) {
      return Optional.empty();
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
