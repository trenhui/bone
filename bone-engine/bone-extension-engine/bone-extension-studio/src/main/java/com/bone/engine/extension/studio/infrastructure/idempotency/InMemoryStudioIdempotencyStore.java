package com.bone.engine.extension.studio.infrastructure.idempotency;

import com.bone.engine.extension.studio.domain.gateway.StudioIdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内幂等存储（单节点联调默认）。 */
public class InMemoryStudioIdempotencyStore implements StudioIdempotencyStore {

  private final Map<String, TimedSnapshot> cache = new ConcurrentHashMap<>();

  @Override
  public Optional<Snapshot> find(String scopeKey) {
    purgeExpired();
    TimedSnapshot entry = cache.get(scopeKey);
    if (entry == null) {
      return Optional.empty();
    }
    return Optional.of(new Snapshot(entry.fingerprint(), entry.snapshotJson()));
  }

  @Override
  public void put(String scopeKey, Snapshot snapshot, Duration ttl) {
    cache.put(
        scopeKey,
        new TimedSnapshot(
            Instant.now(), ttl, snapshot.requestFingerprint(), snapshot.snapshotJson()));
  }

  private void purgeExpired() {
    Instant now = Instant.now();
    cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
  }

  private record TimedSnapshot(
      Instant createdAt, Duration ttl, String fingerprint, String snapshotJson) {
    Instant expiresAt() {
      return createdAt.plus(ttl);
    }
  }
}
