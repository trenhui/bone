package com.bone.engine.extension.studio.infrastructure.idempotency;

import com.bone.engine.extension.studio.domain.gateway.StudioIdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/** Redis 集群幂等存储（多 Studio 实例共享）。 */
@RequiredArgsConstructor
@Slf4j
public class RedisStudioIdempotencyStore implements StudioIdempotencyStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    @Override
    public Optional<Snapshot> find(String scopeKey) {
        String raw = redis.opsForValue().get(redisKey(scopeKey));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        try {
            Payload payload = objectMapper.readValue(raw, Payload.class);
            return Optional.of(new Snapshot(payload.fingerprint(), payload.snapshotJson()));
        } catch (JsonProcessingException ex) {
            log.warn("幂等快照反序列化失败，忽略 key={}", scopeKey);
            redis.delete(redisKey(scopeKey));
            return Optional.empty();
        }
    }

    @Override
    public void put(String scopeKey, Snapshot snapshot, Duration ttl) {
        try {
            Payload payload = new Payload(snapshot.requestFingerprint(), snapshot.snapshotJson());
            redis.opsForValue().set(redisKey(scopeKey), objectMapper.writeValueAsString(payload), ttl);
        } catch (JsonProcessingException ex) {
            log.warn("幂等快照序列化失败，跳过写入 key={}", scopeKey);
        }
    }

    private String redisKey(String scopeKey) {
        return keyPrefix + scopeKey;
    }

    private record Payload(String fingerprint, String snapshotJson) {}
}
