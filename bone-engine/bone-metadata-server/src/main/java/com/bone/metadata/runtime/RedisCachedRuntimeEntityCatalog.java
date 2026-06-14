package com.bone.metadata.runtime;

import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/** Redis 集群缓存（多 metadata-server 实例共享）。 */
@RequiredArgsConstructor
@Slf4j
public class RedisCachedRuntimeEntityCatalog
    implements RuntimeEntityCatalog, RuntimeEntityCacheEvictor {

  private static final String EMPTY_MARKER = "__EMPTY__";

  private final CatalogRuntimeEntityProvider delegate;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final String keyPrefix;
  private final Duration ttl;

  @Override
  public Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode, long tenantId) {
    String redisKey = keyPrefix + tenantId + ":" + entityCode;
    String raw = redis.opsForValue().get(redisKey);
    if (StringUtils.hasText(raw)) {
      if (EMPTY_MARKER.equals(raw)) {
        return Optional.empty();
      }
      try {
        return Optional.of(
            objectMapper.readValue(raw, CachedPayload.class).toEntity(entityCode, tenantId));
      } catch (JsonProcessingException ex) {
        log.warn("RUNTIME 实体缓存反序列化失败，删除 key={}", redisKey);
        redis.delete(redisKey);
      }
    }
    Optional<PublishedRuntimeEntity> loaded = delegate.findPublishedRuntime(entityCode, tenantId);
    try {
      if (loaded.isPresent()) {
        redis
            .opsForValue()
            .set(redisKey, objectMapper.writeValueAsString(CachedPayload.from(loaded.get())), ttl);
      } else {
        redis.opsForValue().set(redisKey, EMPTY_MARKER, Duration.ofSeconds(15));
      }
    } catch (JsonProcessingException ex) {
      log.warn("RUNTIME 实体缓存序列化失败 key={}", redisKey);
    }
    return loaded;
  }

  @Override
  public void evict(String entityCode, long tenantId) {
    redis.delete(keyPrefix + tenantId + ":" + entityCode);
  }

  /** Jackson 可序列化的 RUNTIME 实体快照。 */
  public record CachedPayload(
      String physicalTableName, String primaryKeyColumn, List<ColumnPayload> columns) {

    static CachedPayload from(PublishedRuntimeEntity entity) {
      return new CachedPayload(
          entity.physicalTableName(),
          entity.primaryKeyColumn(),
          entity.columns().stream()
              .map(c -> new ColumnPayload(c.code(), c.required(), c.primaryKey()))
              .toList());
    }

    PublishedRuntimeEntity toEntity(String entityCode, long tenantId) {
      List<RuntimeFieldColumn> cols =
          columns.stream()
              .map(c -> new RuntimeFieldColumn(c.code(), c.required(), c.primaryKey()))
              .toList();
      return new PublishedRuntimeEntity(
          entityCode, physicalTableName, primaryKeyColumn, tenantId, cols);
    }

    record ColumnPayload(String code, boolean required, boolean primaryKey) {}
  }
}
