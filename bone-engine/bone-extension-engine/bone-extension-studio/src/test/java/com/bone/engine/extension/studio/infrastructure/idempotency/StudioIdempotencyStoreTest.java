package com.bone.engine.extension.studio.infrastructure.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.domain.gateway.StudioIdempotencyStore;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudioIdempotencyStoreTest {

    @Test
    @DisplayName("内存存储写入后可读取")
    void inMemoryRoundTrip() {
        StudioIdempotencyStore store = new InMemoryStudioIdempotencyStore();
        StudioIdempotencyStore.Snapshot snapshot =
                new StudioIdempotencyStore.Snapshot("fp1", "{\"status\":200}");

        store.put("scope-1", snapshot, Duration.ofMinutes(5));

        assertTrue(store.find("scope-1").isPresent());
        assertEquals("fp1", store.find("scope-1").get().requestFingerprint());
        assertEquals("{\"status\":200}", store.find("scope-1").get().snapshotJson());
    }
}
