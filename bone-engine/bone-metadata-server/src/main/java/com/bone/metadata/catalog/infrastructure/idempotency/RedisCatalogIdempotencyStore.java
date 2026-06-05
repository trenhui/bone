package com.bone.metadata.catalog.infrastructure.idempotency;

import com.bone.metadata.catalog.domain.gateway.CatalogIdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/** Redis 集群幂等存储（多 metadata-server 实例共享）。 */
@RequiredArgsConstructor
@Slf4j
public class RedisCatalogIdempotencyStore implements CatalogIdempotencyStore {

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final String keyPrefix;

  @Override
  public Optional<Snapshot> find(String scopeKey) {
    String raw = redis.opsForValue().get(keyPrefix + scopeKey);
    if (!StringUtils.hasText(raw)) {
      return Optional.empty();
    }
    try {
      Payload payload = objectMapper.readValue(raw, Payload.class);
      return Optional.of(new Snapshot(payload.fingerprint(), payload.snapshotJson()));
    } catch (JsonProcessingException ex) {
      log.warn("幂等快照反序列化失败，删除 key={}", scopeKey);
      redis.delete(keyPrefix + scopeKey);
      return Optional.empty();
    }
  }

  @Override
  public void put(String scopeKey, Snapshot snapshot, Duration ttl) {
    try {
      Payload payload = new Payload(snapshot.requestFingerprint(), snapshot.snapshotJson());
      redis.opsForValue().set(keyPrefix + scopeKey, objectMapper.writeValueAsString(payload), ttl);
    } catch (JsonProcessingException ex) {
      log.warn("幂等快照序列化失败，跳过 key={}", scopeKey);
    }
  }

  private record Payload(String fingerprint, String snapshotJson) {}
}
