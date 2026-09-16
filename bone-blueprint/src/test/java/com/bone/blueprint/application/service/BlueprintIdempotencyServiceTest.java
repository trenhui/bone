package com.bone.blueprint.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.IdempotencyStore;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import java.net.URI;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** 幂等服务单元测试：契约见《Bone-API-规范》§6.1/§8（同键同 body 重放、同键异 body 409、TTL 24h）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlueprintIdempotencyServiceTest {

  private static final String KEY = "6f1d3a2c-1b1e-4a0e-9a3e-9f1d3a2c1b1e";

  private static final String METHOD = "POST";

  private static final String PATH = "/api/v1/orders";

  @Mock private IdempotencyStore store;

  @InjectMocks private BlueprintIdempotencyService service;

  @BeforeEach
  void setUp() {
    // 作用域键含身份维度，取自 MDC（由请求上下文过滤器与认证过滤器写入）
    MDC.put("tenantId", "7");
    MDC.put("userId", "9");
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void fingerprintIsStableAndDistinguishesPayloads() {
    assertEquals(
        BlueprintIdempotencyService.fingerprint("{\"a\":1}"),
        BlueprintIdempotencyService.fingerprint("{\"a\":1}"));
    assertNotEquals(
        BlueprintIdempotencyService.fingerprint("{\"a\":1}"),
        BlueprintIdempotencyService.fingerprint("{\"a\":2}"));
  }

  @Test
  void scopeKeyContainsTenantUserKeyMethodAndPath() {
    String scopeKey = service.scopeKey(KEY, METHOD, PATH);

    assertTrue(scopeKey.startsWith("7|9|" + KEY + "|POST|/api/v1/orders"), scopeKey);
  }

  @Test
  void replayWithoutKeyReturnsEmpty() {
    assertTrue(service.replay(null, METHOD, PATH, "{}").isEmpty());
    assertTrue(service.replay("  ", METHOD, PATH, "{}").isEmpty());
  }

  @Test
  void replayWhenSnapshotMissingReturnsEmpty() {
    when(store.find(any())).thenReturn(Optional.empty());

    assertTrue(service.replay(KEY, METHOD, PATH, "{}").isEmpty());
  }

  @Test
  void replayWithDifferentFingerprintConflicts() {
    when(store.find(any()))
        .thenReturn(Optional.of(new IdempotencyStore.Snapshot("其它指纹", "{\"status\":201}")));

    BizException ex =
        assertThrows(BizException.class, () -> service.replay(KEY, METHOD, PATH, "{\"a\":1}"));

    assertEquals(409, ex.getCode());
    assertTrue(ex.getMessage().contains(BlueprintErrorCodes.IDEMPOTENCY_CONFLICT));
  }

  @Test
  void replayReturnsStoredStatusAndLocation() {
    String payload = "{\"customerId\":1}";
    String fingerprint = service.fingerprintOf(payload);
    String snapshotJson =
        "{\"status\":201,\"location\":\"/api/v1/orders/42\","
            + "\"body\":{\"success\":true,\"code\":200,\"message\":\"操作成功\",\"data\":{\"id\":42}}}";
    when(store.find(any()))
        .thenReturn(Optional.of(new IdempotencyStore.Snapshot(fingerprint, snapshotJson)));

    Optional<ResponseEntity<ApiResponse<Object>>> replayed =
        service.replay(KEY, METHOD, PATH, payload);

    assertTrue(replayed.isPresent());
    assertEquals(201, replayed.get().getStatusCode().value());
    assertEquals("/api/v1/orders/42", replayed.get().getHeaders().getFirst(HttpHeaders.LOCATION));
  }

  @Test
  void rememberStoresSnapshotWith24HoursTtl() {
    ResponseEntity<ApiResponse<String>> response =
        ResponseEntity.created(URI.create("/api/v1/orders/42")).body(ApiResponse.success("42"));

    service.remember(KEY, METHOD, PATH, "{\"customerId\":1}", response);

    ArgumentCaptor<IdempotencyStore.Snapshot> captor =
        ArgumentCaptor.forClass(IdempotencyStore.Snapshot.class);
    // TTL 必须是 24h（API 规范 §8），不能顺手改成更短/更长
    verify(store)
        .put(eq(service.scopeKey(KEY, METHOD, PATH)), captor.capture(), eq(Duration.ofHours(24)));
    assertTrue(captor.getValue().snapshotJson().contains("\"status\":201"));
    assertTrue(captor.getValue().snapshotJson().contains("/api/v1/orders/42"));
  }
}
