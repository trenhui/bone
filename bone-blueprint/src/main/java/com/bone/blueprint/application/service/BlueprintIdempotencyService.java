package com.bone.blueprint.application.service;

import com.bone.blueprint.application.port.out.IdempotencyStore;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 幂等写服务（《Bone-API-规范》§6.1 / §8）。
 *
 * <p><b>协议契约</b>：请求带 {@code Idempotency-Key}（UUID v4）时——相同 body 重复请求返回<strong>同一响应</strong> （含状态码与
 * {@code Location}）；键相同但 body 不同返回 <strong>409</strong> + {@code COMMON_IDEMPOTENCY_CONFLICT}；快照
 * TTL <strong>24h</strong>，过期后同一键可复用。
 *
 * <p><b>与平台既有实现同范式</b>：{@code StudioIdempotencyService} / {@code CatalogIdempotencyService} 走的是 「服务
 * + Store 端口 + 落库/Redis 实现」同一条路；本类是它们的 blueprint 版本（存储实现换成 MySQL 表 {@code
 * bp_idempotency_record}，因本模块无 Redis 依赖）。这样其他模块照抄时不会遇到第二种形态。
 *
 * <p><b>作用域</b>：{@code 租户 | 用户 | 键 | 方法 | 路径}。含用户与路径是必要的——键由客户端生成，若不按用户隔离，
 * 同一租户内两个用户偶然用同一个键就会互相重放对方的响应。身份取自 MDC（由认证过滤器与请求上下文过滤器写入）， 因此本服务必须在 MDC 就绪后调用。
 *
 * <p><b>分层折中</b>：返回值/入参用到 {@code ResponseEntity}/{@code ApiResponse} 这类 HTTP 类型，严格说属 adapter
 * 语义（E-10.1）。这里与平台既有模块保持一致以降低照抄成本；若要严格分层，应把快照模型改为协议无关对象、由 adapter 渲染（属目标态改造）。
 */
@Slf4j
@Service
public class BlueprintIdempotencyService {

  /** 幂等键请求头名（API 规范 §6.1）。 */
  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  /** 快照保留时长（API 规范 §8：TTL 24h）。 */
  private static final Duration TTL = Duration.ofHours(24);

  private static final String KEY_SEPARATOR = "|";

  private static final String EMPTY_FINGERPRINT = "empty";

  private final IdempotencyStore store;

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public BlueprintIdempotencyService(IdempotencyStore store) {
    this.store = store;
  }

  /** 请求是否声明了幂等键（未声明则整个机制不介入，与平台其它模块一致）。 */
  public boolean hasKey(String idempotencyKey) {
    return StringUtils.hasText(idempotencyKey);
  }

  /**
   * 重放历史响应。
   *
   * @param payload 请求载荷（命令对象或原始 body 字符串）：指纹在服务内计算，调用方无需关心序列化
   * @return 命中且未过期时返回历史响应；未命中返回 {@link Optional#empty()}
   * @throws BizException 409：同一键被用于不同请求体
   */
  public <T> Optional<ResponseEntity<ApiResponse<T>>> replay(
      String idempotencyKey, String method, String path, Object payload) {
    if (!hasKey(idempotencyKey)) {
      return Optional.empty();
    }
    String requestFingerprint = fingerprintOf(payload);
    Optional<IdempotencyStore.Snapshot> found = store.find(scopeKey(idempotencyKey, method, path));
    if (found.isEmpty()) {
      return Optional.empty();
    }
    IdempotencyStore.Snapshot snapshot = found.get();
    if (!snapshot.requestFingerprint().equals(requestFingerprint)) {
      throw new BizException(
          409, BlueprintErrorCodes.IDEMPOTENCY_CONFLICT + ": Idempotency-Key 已用于不同请求体");
    }
    try {
      JsonNode node = objectMapper.readTree(snapshot.snapshotJson());
      ApiResponse<T> body =
          objectMapper.convertValue(
              node.get("body"),
              objectMapper
                  .getTypeFactory()
                  .constructParametricType(ApiResponse.class, Object.class));
      ResponseEntity.BodyBuilder builder = ResponseEntity.status(node.get("status").asInt());
      if (node.hasNonNull("location")) {
        builder.header(HttpHeaders.LOCATION, node.get("location").asText());
      }
      log.info("[Biz] 幂等键命中，重放历史响应: key={}, status={}", idempotencyKey, node.get("status").asInt());
      return Optional.of(builder.body(body));
    } catch (JsonProcessingException ex) {
      // 快照损坏：宁可重新执行也不返回坏响应（失败会走异常处理并留日志）
      log.warn("幂等快照解析失败，按未命中处理: key={}", idempotencyKey, ex);
      return Optional.empty();
    }
  }

  /** 记录本次响应，供后续同键请求重放。仅在成功路径调用（失败让客户端可用同键重试）。 */
  public <T> void remember(
      String idempotencyKey,
      String method,
      String path,
      Object payload,
      ResponseEntity<ApiResponse<T>> response) {
    if (!hasKey(idempotencyKey) || response == null || response.getBody() == null) {
      return;
    }
    String requestFingerprint = fingerprintOf(payload);
    try {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("status", response.getStatusCode().value());
      String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
      if (location != null) {
        node.put("location", location);
      }
      node.set("body", toJsonBody(response.getBody()));
      store.put(
          scopeKey(idempotencyKey, method, path),
          new IdempotencyStore.Snapshot(requestFingerprint, objectMapper.writeValueAsString(node)),
          TTL);
    } catch (JsonProcessingException ex) {
      // 快照写不进去不应让业务请求失败：退化为「无幂等保护」，但必须留痕
      log.warn("幂等快照序列化失败，本次不记录: key={}", idempotencyKey, ex);
    }
  }

  /**
   * 请求体指纹：SHA-256 十六进制。
   *
   * <p>用「键 + 指纹」而非「键 + body 原文」比对：body 可能含不该落库的字段（凭据等），存哈希即可判定异同。
   */
  public static String fingerprint(String rawBody) {
    if (!StringUtils.hasText(rawBody)) {
      return EMPTY_FINGERPRINT;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawBody.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      // SHA-256 一定存在；兜底保持「同 body 同结果」即可
      return Integer.toHexString(rawBody.hashCode());
    }
  }

  /**
   * 载荷指纹：命令对象/请求体序列化后取 SHA-256。
   *
   * <p>序列化失败<strong>不允许降级</strong>为「空指纹」——否则两个不同请求会得到同一指纹，第二次请求会拿到第一次的
   * 响应（比不幂等更糟的错误结果）。此处直接失败，让调用方按系统错误处理。
   */
  public String fingerprintOf(Object payload) {
    if (payload == null) {
      return EMPTY_FINGERPRINT;
    }
    if (payload instanceof String raw) {
      return fingerprint(raw);
    }
    try {
      return fingerprint(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("幂等指纹计算失败（无法序列化载荷）: " + payload.getClass().getName(), ex);
    }
  }

  /**
   * 作用域键：{@code 租户|用户|幂等键|方法|路径}。
   *
   * <p>身份取 MDC（{@code tenantId}/{@code userId}，由 {@code BoneRequestContextFilter} 与认证过滤器写入）。
   */
  public String scopeKey(String idempotencyKey, String method, String path) {
    return nullToEmpty(MDC.get("tenantId"))
        + KEY_SEPARATOR
        + nullToEmpty(MDC.get("userId"))
        + KEY_SEPARATOR
        + idempotencyKey.trim()
        + KEY_SEPARATOR
        + nullToEmpty(method)
        + KEY_SEPARATOR
        + nullToEmpty(path);
  }

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
