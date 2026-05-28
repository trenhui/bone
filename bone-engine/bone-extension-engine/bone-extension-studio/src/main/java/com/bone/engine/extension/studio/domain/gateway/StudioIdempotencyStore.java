package com.bone.engine.extension.studio.domain.gateway;

import java.time.Duration;
import java.util.Optional;

/** Studio 幂等快照出站端口（内存或 Redis 实现）。 */
public interface StudioIdempotencyStore {

    Optional<Snapshot> find(String scopeKey);

    void put(String scopeKey, Snapshot snapshot, Duration ttl);

    record Snapshot(String requestFingerprint, String snapshotJson) {}
}
