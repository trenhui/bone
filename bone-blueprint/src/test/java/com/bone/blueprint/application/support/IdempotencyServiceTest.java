package com.bone.blueprint.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.IdempotentException;
import com.bone.core.idempotency.IdempotencyFingerprint;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.idempotency.IdempotencyStore;
import com.bone.core.model.ApiResponse;
import com.bone.core.tenant.context.TenantContext;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

/** 幂等服务单元测试：契约见《Bone-API-规范》§6.1/§8（同键同 body 重放、同键异 body 409、TTL 24h）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyServiceTest {

  private static final String KEY = "6f1d3a2c-1b1e-4a0e-9a3e-9f1d3a2c1b1e";

  private static final String METHOD = "POST";

  private static final String PATH = "/api/v1/orders";

  @Mock private IdempotencyStore store;

  @InjectMocks private IdempotencyService service;

  @BeforeEach
  void setUp() {
    // 作用域键含身份维度，取自 MDC（由请求上下文过滤器与认证过滤器写入）
    TenantContext.setTenantId("7");
    MDC.put("userId", "9");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    MDC.clear();
  }

  @Test
  void fingerprintIsStableAndDistinguishesPayloads() {
    assertEquals(
        IdempotencyFingerprint.fingerprint("{\"a\":1}"),
        IdempotencyFingerprint.fingerprint("{\"a\":1}"));
    assertNotEquals(
        IdempotencyFingerprint.fingerprint("{\"a\":1}"),
        IdempotencyFingerprint.fingerprint("{\"a\":2}"));
  }

  @Test
  void scopeKeyContainsTenantUserKeyMethodAndPath() {
    String scopeKey = service.buildScopeKey(KEY, MDC.get("userId"), METHOD, PATH);

    assertTrue(scopeKey.startsWith("7|9|" + KEY + "|POST|/api/v1/orders"), scopeKey);
  }

  @Test
  void replayWithoutKeyReturnsEmpty() {
    assertTrue(service.replay(null, MDC.get("userId"), METHOD, PATH, "{}").isEmpty());
    assertTrue(service.replay("  ", MDC.get("userId"), METHOD, PATH, "{}").isEmpty());
  }

  @Test
  void replayWhenSnapshotMissingReturnsEmpty() {
    when(store.find(any())).thenReturn(Optional.empty());

    assertTrue(service.replay(KEY, MDC.get("userId"), METHOD, PATH, "{}").isEmpty());
  }

  @Test
  void replayWithDifferentFingerprintConflicts() {
    when(store.find(any()))
        .thenReturn(Optional.of(new IdempotencyStore.Snapshot("其它指纹", "{\"status\":201}")));

    IdempotentException ex =
        assertThrows(
            IdempotentException.class,
            () -> service.replay(KEY, MDC.get("userId"), METHOD, PATH, "{\"a\":1}"));

    assertTrue(ex.getMessage().contains("Idempotency-Key"));
  }

  @Test
  void replayReturnsStoredStatusAndLocation() {
    String payload = "{\"customerId\":1}";
    String fingerprint = IdempotencyFingerprint.fingerprintOf(payload);
    String snapshotJson =
        "{\"status\":201,\"location\":\"/api/v1/orders/42\","
            + "\"body\":{\"success\":true,\"code\":200,\"message\":\"操作成功\",\"data\":{\"id\":42}}}";
    when(store.find(any()))
        .thenReturn(Optional.of(new IdempotencyStore.Snapshot(fingerprint, snapshotJson)));

    Optional<IdempotencyService.ReplayedResponse> replayed =
        service.replay(KEY, MDC.get("userId"), METHOD, PATH, payload);

    assertTrue(replayed.isPresent());
    assertEquals(201, replayed.get().status());
    assertEquals("/api/v1/orders/42", replayed.get().location());
  }

  @Test
  void rememberStoresSnapshotWith24HoursTtl() {
    IdempotencyService.ReplayedResponse response =
        new IdempotencyService.ReplayedResponse(
            201, "/api/v1/orders/42", ApiResponse.success("42"));

    service.remember(KEY, MDC.get("userId"), METHOD, PATH, "{\"customerId\":1}", response);

    ArgumentCaptor<IdempotencyStore.Snapshot> captor =
        ArgumentCaptor.forClass(IdempotencyStore.Snapshot.class);
    // TTL 必须是 24h（API 规范 §8），不能顺手改成更短/更长
    verify(store)
        .put(
            eq(service.buildScopeKey(KEY, MDC.get("userId"), METHOD, PATH)),
            captor.capture(),
            eq(Duration.ofHours(24)));
    assertTrue(captor.getValue().snapshotJson().contains("\"status\":201"));
    assertTrue(captor.getValue().snapshotJson().contains("/api/v1/orders/42"));
  }
}
