package com.bone.metadata.catalog.application.support;

import com.bone.core.model.ApiResponse;
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

/**
 * 元数据 catalog / EAV / runtime 写操作幂等（对齐 Stripe Idempotency-Key）。
 *
 * <p><b>TTL 配置化</b>（原硬编码 24h，Runtime §4.6.1 缺陷 #2）：资金类动作 24h 后重放同一 Key 会
 * 二次记账——幂等记录已过期，而状态机再校验也不足够（若凭证已生成但状态回滚）。 保存时长必须能覆盖业务窗口，故改为配置项 {@code
 * bone.metadata.idempotency.ttl}。
 */
@Service
public class CatalogIdempotencySupport {

  private final ObjectMapper objectMapper;
  private final CatalogIdempotencyStore store;
  private final Duration ttl;

  public CatalogIdempotencySupport(
      ObjectMapper objectMapper,
      CatalogIdempotencyStore store,
      @org.springframework.beans.factory.annotation.Value(
              "${bone.metadata.catalog.idempotency.ttl:PT24H}")
          Duration ttl) {
    this.objectMapper =
        objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.store = store;
    this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofHours(24) : ttl;
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

  /**
   * 幂等作用域：<strong>租户 + Key + 方法 + 路径，不含 user</strong>（Runtime §4.6.1 缺陷 #1）。
   *
   * <p><b>为什么要去掉 user</b>：幂等的语义是「同一个业务意图只执行一次」。绑 user 后， 同一 Key
   * 被两个用户各用一次会<strong>执行两次</strong>——网关重试、换标签页、重试代理都能击穿它。 Stripe 的 scope 是 account + key，同样不含 user。
   *
   * <p>请求体差异由 {@code requestFingerprint} 承担（同 Key 不同体 → 409 {@code
   * CatalogIdempotencyConflictException}），与 user 无关。
   */
  public String scopeKey(String idempotencyKey, String method, String path) {
    String tenant = nullToEmpty(MDC.get("tenantId"));
    return tenant + "|" + idempotencyKey.trim() + "|" + method + "|" + path;
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
          ttl);
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
          ttl);
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
