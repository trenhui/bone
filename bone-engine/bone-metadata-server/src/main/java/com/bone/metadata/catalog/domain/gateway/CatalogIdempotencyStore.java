package com.bone.metadata.catalog.domain.gateway;

import java.time.Duration;
import java.util.Optional;

/** catalog 幂等快照出站端口（进程内或 Redis）。 */
public interface CatalogIdempotencyStore {

  Optional<Snapshot> find(String scopeKey);

  void put(String scopeKey, Snapshot snapshot, Duration ttl);

  record Snapshot(String requestFingerprint, String snapshotJson) {}
}
