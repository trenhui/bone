package com.bone.core.idempotency;

import com.bone.core.exception.IdempotentException;
import com.bone.core.model.ApiResponse;
import com.bone.core.tenant.context.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 幂等编排服务（跨模块复用）。
 *
 * <p><b>协议契约</b>：请求带幂等键时——相同 body 重复请求返回<strong>同一响应</strong>（含状态码与 {@code Location}）； 键相同但 body
 * 不同抛 {@link IdempotentException}（冲突）；快照 TTL 默认 24h，过期后同一键可复用。
 *
 * <p><b>分层</b>：本服务属「技术能力编排」层，被业务模块的 application 层调用（Controller / Consumer / Job）。 协议无关——入参是
 * scopeKey + payload，出参是 {@link ReplayedResponse}，不接收也不返回 HTTP 类型。 HTTP 状态码与响应头由 adapter 层渲染。
 *
 * <p><b>作用域键</b>：{@code 租户 | 用户 | 幂等键 | 方法 | 路径}。 租户从 {@link TenantContext}
 * 自动取，用户由调用方显式传入（业务模块的身份来源可能不同：userId / operatorId / bizIdentity）。
 *
 * @see IdempotencyStore 存储契约（业务模块的 infrastructure 层实现）
 * @see IdempotencyFingerprint 纯指纹工具
 */
@Slf4j
@Service
public class IdempotencyService {

  /** 幂等键请求头名（API 规范 §6.1）。 */
  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  /** 快照保留时长（默认 TTL 24h，可在 remember 时覆盖）。 */
  public static final Duration DEFAULT_TTL = Duration.ofHours(24);

  private static final String KEY_SEPARATOR = "|";

  /**
   * 幂等快照的<strong>协议无关</strong>视图：只承载「响应事实」——状态码、可选的 {@code Location}、统一信封体。
   *
   * <p>为何不用 {@code ResponseEntity}：HTTP 语义只属于 adapter。应用层返回本对象，快照落库的 JSON 结构不变。
   */
  public record ReplayedResponse(int status, String location, ApiResponse<?> body) {}

  private final IdempotencyStore store;

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public IdempotencyService(IdempotencyStore store) {
    this.store = store;
  }

  // ===================== 作用域键 =====================

  /**
   * 构建作用域键（租户自动从 {@link TenantContext} 取）。
   *
   * @param idempotencyKey 客户端生成的幂等键（UUID v4）
   * @param userId 当前用户标识（由调用方决定来源）
   * @param method HTTP 方法（或其他入口类型标识）
   * @param path 请求路径（或操作标识）
   * @return 拼接后的作用域键
   */
  public String buildScopeKey(String idempotencyKey, String userId, String method, String path) {
    return buildScopeKeyInternal(TenantContext.getTenantId(), userId, idempotencyKey, method, path);
  }

  /** 显式指定租户的作用域键（异步场景 TenantContext 缺失时可手动传入）。 */
  public String buildScopeKey(
      String tenantId, String userId, String idempotencyKey, String method, String path) {
    return buildScopeKeyInternal(tenantId, userId, idempotencyKey, method, path);
  }

  private static String buildScopeKeyInternal(
      String tenantId, String userId, String idempotencyKey, String method, String path) {
    return nullToEmpty(tenantId)
        + KEY_SEPARATOR
        + nullToEmpty(userId)
        + KEY_SEPARATOR
        + idempotencyKey.trim()
        + KEY_SEPARATOR
        + nullToEmpty(method)
        + KEY_SEPARATOR
        + nullToEmpty(path);
  }

  // ===================== 核心编排 =====================

  /** 请求是否声明了幂等键（未声明则整个机制不介入）。 */
  public boolean hasKey(String idempotencyKey) {
    return StringUtils.hasText(idempotencyKey);
  }

  /**
   * 重放历史响应。
   *
   * @param scopeKey 作用域键（通常由 {@link #buildScopeKey} 构建）
   * @param payload 请求载荷（命令对象或原始 body 字符串）：指纹在服务内计算
   * @return 命中且未过期时返回历史响应；未命中返回 {@link Optional#empty()}
   * @throws IdempotentException 键相同但请求体不同（冲突）
   */
  public Optional<ReplayedResponse> replay(String scopeKey, Object payload) {
    Optional<IdempotencyStore.Snapshot> found = store.find(scopeKey);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    IdempotencyStore.Snapshot snapshot = found.get();
    String requestFingerprint = IdempotencyFingerprint.fingerprintOf(payload);
    if (!snapshot.requestFingerprint().equals(requestFingerprint)) {
      throw new IdempotentException("Idempotency-Key 已用于不同请求体（scopeKey=" + scopeKey + "）");
    }
    try {
      JsonNode node = objectMapper.readTree(snapshot.snapshotJson());
      ApiResponse<?> body = objectMapper.convertValue(node.get("body"), ApiResponse.class);
      int status = node.get("status").asInt();
      String location = node.hasNonNull("location") ? node.get("location").asText() : null;
      log.info("[Biz] 幂等键命中，重放历史响应: scopeKey={}, status={}", scopeKey, status);
      return Optional.of(new ReplayedResponse(status, location, body));
    } catch (JsonProcessingException ex) {
      // 快照损坏：宁可重新执行也不返回坏响应（失败会走异常处理并留日志）
      log.warn("幂等快照解析失败，按未命中处理: scopeKey={}", scopeKey, ex);
      return Optional.empty();
    }
  }

  // ===================== 便捷重载（自动拼 scopeKey + hasKey 检查） =====================

  /**
   * 便捷版重放：自动拼 scopeKey（租户从 {@link TenantContext} 取），未声明幂等键时直接返回 {@link Optional#empty()}。
   *
   * <p>等价于：{@code if (!hasKey(key)) return empty; return replay(buildScopeKey(key, userId, method,
   * path), payload);}
   *
   * @param idempotencyKey 客户端幂等键（未传入/为空时直接跳过）
   * @param userId 当前用户标识
   * @param method HTTP 方法（或入口类型标识）
   * @param path 请求路径（或操作标识）
   * @param payload 请求载荷
   * @return 命中历史响应返回它；未命中或未声明幂等键返回 {@link Optional#empty()}
   * @throws IdempotentException 键相同但请求体不同
   */
  public Optional<ReplayedResponse> replay(
      String idempotencyKey, String userId, String method, String path, Object payload) {
    if (!hasKey(idempotencyKey)) {
      return Optional.empty();
    }
    return replay(buildScopeKey(idempotencyKey, userId, method, path), payload);
  }

  /** 便捷版 remember：自动拼 scopeKey（租户从 {@link TenantContext} 取），未声明幂等键时跳过。 */
  public void remember(
      String idempotencyKey,
      String userId,
      String method,
      String path,
      Object payload,
      ReplayedResponse response) {
    if (!hasKey(idempotencyKey)) {
      return;
    }
    remember(buildScopeKey(idempotencyKey, userId, method, path), payload, response);
  }

  // ===================== 底层编排（需自行拼 scopeKey） =====================

  /**
   * 记录本次响应，供后续同键请求重放。仅在成功路径调用（失败让客户端可用同键重试）。
   *
   * @param scopeKey 作用域键
   * @param payload 请求载荷（用于计算指纹）
   * @param response 要记录的响应事实
   */
  public void remember(String scopeKey, Object payload, ReplayedResponse response) {
    remember(scopeKey, payload, response, DEFAULT_TTL);
  }

  /** 带自定义 TTL 的 remember。 */
  public void remember(String scopeKey, Object payload, ReplayedResponse response, Duration ttl) {
    if (response == null || response.body() == null) {
      return;
    }
    String requestFingerprint = IdempotencyFingerprint.fingerprintOf(payload);
    try {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("status", response.status());
      if (response.location() != null) {
        node.put("location", response.location());
      }
      node.set("body", toJsonBody(response.body()));
      store.put(
          scopeKey,
          new IdempotencyStore.Snapshot(requestFingerprint, objectMapper.writeValueAsString(node)),
          ttl);
    } catch (JsonProcessingException ex) {
      // 快照写不进去不应让业务请求失败：退化为「无幂等保护」，但必须留痕
      log.warn("幂等快照序列化失败，本次不记录: scopeKey={}", scopeKey, ex);
    }
  }

  // ===================== 内部工具 =====================

  private ObjectNode toJsonBody(ApiResponse<?> body) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("success", body.getSuccess());
    node.put("code", body.getCode());
    node.put("message", body.getMessage());
    if (body.getTimestamp() != null) {
      node.put("timestamp", body.getTimestamp());
    }
    node.set("data", objectMapper.valueToTree(body.getData()));
    return node;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
