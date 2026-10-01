package com.bone.blueprint.infrastructure.gateway.masterdata;

import com.bone.blueprint.domain.gateway.MasterDataGateway;
import com.bone.blueprint.infrastructure.config.MasterDataConsumptionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 主数据消费 ACL 适配器（防腐蚀层）：通过网关 REST 调用 bone-masterdata。
 *
 * <p><b>为何是 ACL 而不是直连库</b>：跨模块直读对方的表会把 masterdata 的存储结构泄漏进 blueprint， 对方一改列名/键名就跨模块炸裂。REST 契约（实体列表
 * / 记录列表）是 masterdata 对外的官方防腐面。
 *
 * <p><b>查询模型</b>：主路径为 masterdata 的按业务键点查契约 {@code GET /records/by-code}—— 「已发布 + 当前版本 +
 * 生效窗口含此刻」的门禁收敛在主数据侧，过期价格在这里就被拦下， 消费方不再为查一条记录整实体全量拉取。 回退路径保留实体全量快照 + 内存匹配：存量记录可能尚未补登 {@code
 * record_code}（业务键只存在于 data JSON），点查按编码找不到， 仍需按 JSON 业务键兜底（商品编码 / 等级编码）。点查未命中做短 TTL
 * 负缓存，防止「查不存在的编码」打穿到 masterdata。
 *
 * <p><b>降级</b>：登录/查询任一环节失败按「不可达」处理——返回 false/empty 并 WARN（见端口约定）， 结果带 TTL 缓存避免主数据抖动放大成下单失败。
 */
@Slf4j
@Component
public class MasterDataAclAdapter implements MasterDataGateway {

  private static final long TOKEN_TTL_SECONDS = 300;

  /** 未命中触发的强制回源最小间隔：防止"查不存在的编码"打穿到 masterdata。 */
  private static final long FORCED_REFRESH_MIN_INTERVAL_MS = 1000L;

  /** 点查未命中的负缓存 TTL：同一编码短时间内的反复未命中不再回源。 */
  private static final long MISS_CACHE_TTL_MS = 5000L;

  private final MasterDataConsumptionProperties props;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** token 缓存：值同时记获取时间，过期重登。 */
  private volatile String cachedToken;

  private volatile Instant tokenFetchedAt = Instant.EPOCH;

  /** 已发布记录快照缓存：实体编码 → (抓取时间, 记录列表)。 */
  private final Map<String, Snapshot> snapshotCache = new ConcurrentHashMap<>();

  /** 实体编码 → 已发布实体 ID（实体发布状态基本不变，进程内常驻缓存）。 */
  private final Map<String, Long> entityIdCache = new ConcurrentHashMap<>();

  /** 点查负缓存：实体编码|业务编码 → 上次未命中时刻。 */
  private final Map<String, Long> missCache = new ConcurrentHashMap<>();

  /** 强制回源时间戳：实体编码 → 上次强制刷新时刻（限流用）。 */
  private final Map<String, Long> forcedRefreshAt = new ConcurrentHashMap<>();

  public MasterDataAclAdapter(MasterDataConsumptionProperties props) {
    this.props = props;
    this.restClient =
        RestClient.builder()
            .baseUrl(props.getBaseUrl())
            .requestFactory(
                new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                  {
                    setConnectTimeout(2000);
                    setReadTimeout(4000);
                  }
                })
            .build();
  }

  @Override
  public Optional<ProductView> findPublishedProduct(String productCode) {
    if (productCode == null || productCode.isBlank()) {
      return Optional.empty();
    }
    try {
      return findPublishedRecord(props.getProductEntityCode(), "code", productCode)
          .flatMap(
              view -> {
                // 业务主键优先取 recordCode / displayName（主数据的一等公民列），
                // 缺失时才回退到 data JSON —— 存量记录在 record_code 补登前只有 JSON 里的键。
                String code =
                    view.recordCode() != null
                        ? view.recordCode()
                        : stringValue(view.data(), "code");
                String name =
                    view.displayName() != null
                        ? view.displayName()
                        : stringValue(view.data(), "name");
                Optional<BigDecimal> price = readDecimal(view.data(), "price");
                if (code == null || name == null || price.isEmpty()) {
                  return Optional.empty();
                }
                return Optional.of(new ProductView(code, name, price.get()));
              });
    } catch (Exception e) {
      log.warn("商品主数据查询失败（按不可达降级）: productCode={}", productCode, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> findCustomerLevelCode(String customerCode) {
    if (customerCode == null || customerCode.isBlank()) {
      return Optional.empty();
    }
    try {
      return findPublishedRecord(props.getCustomerEntityCode(), "customer_code", customerCode)
          .map(view -> stringValue(view.data(), "level_code"))
          .filter(code -> code != null && !code.isBlank());
    } catch (Exception e) {
      log.warn("客户主数据查询失败（按不可达降级）: customerCode={}", customerCode, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<BigDecimal> findLevelDiscountRate(String levelCode) {
    if (levelCode == null || levelCode.isBlank()) {
      return Optional.empty();
    }
    return findPublishedRecord(props.getLevelEntityCode(), "level_code", levelCode)
        .flatMap(view -> readDecimal(view.data(), "discount_rate"))
        .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0 && rate.compareTo(BigDecimal.ONE) <= 0);
  }

  // ===================== 内部：实体定位 + 记录快照 =====================

  private Optional<RecordView> findPublishedRecord(
      String entityCode, String bizKeyField, String bizKeyValue) {
    try {
      // 主路径：按业务键点查（masterdata 侧已收敛 发布+当前版本+生效窗口 三道门禁）
      Optional<RecordView> direct = fetchByCode(entityCode, bizKeyValue);
      if (direct.isPresent()) {
        return direct;
      }
      // 回退：存量记录可能尚未补登 record_code（业务键只存在于 data JSON），点查按编码找不到，
      // 沿用快照 + JSON 业务键匹配。未命中不一定是不存在：主数据可能刚刚发布，
      // 按 cache-aside 惯例补一次强制刷新再试。
      Snapshot snapshot = snapshotOf(entityCode, false);
      Optional<RecordView> hit = matchIn(snapshot, bizKeyField, bizKeyValue);
      if (hit.isPresent()) {
        return hit;
      }
      Snapshot refreshed = snapshotOf(entityCode, true);
      return matchIn(refreshed, bizKeyField, bizKeyValue);
    } catch (Exception e) {
      log.warn("主数据查询失败（按不可达降级）: entityCode={}, key={}", entityCode, bizKeyValue, e);
      return Optional.empty();
    }
  }

  /** 按业务编码点查当前生效记录；未命中（含负缓存期内）返回 empty。 */
  private Optional<RecordView> fetchByCode(String entityCode, String recordCode) throws Exception {
    String missKey = entityCode + "|" + recordCode;
    Long lastMiss = missCache.get(missKey);
    if (lastMiss != null && Instant.now().toEpochMilli() - lastMiss < MISS_CACHE_TTL_MS) {
      return Optional.empty();
    }
    Long entityId = publishedEntityIdOf(entityCode);
    if (entityId == null) {
      return Optional.empty();
    }
    JsonNode resp =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/masterdata/records/by-code")
                        .queryParam("masterDataEntityId", entityId)
                        .queryParam("code", recordCode)
                        .build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    JsonNode node = resp == null ? null : resp.path("data");
    if (node == null || !node.isObject() || node.isNull()) {
      missCache.put(missKey, Instant.now().toEpochMilli());
      return Optional.empty();
    }
    return Optional.of(toRecordView(node));
  }

  private RecordView toRecordView(JsonNode node) throws Exception {
    JsonNode dataNode = node.path("data");
    Map<String, Object> data =
        dataNode.isTextual()
            ? objectMapper.readValue(dataNode.asText(), Map.class)
            : dataNode.isNull() ? Map.of() : objectMapper.convertValue(dataNode, Map.class);
    return new RecordView(
        nullIfBlank(node.path("recordCode").asText(null)),
        nullIfBlank(node.path("displayName").asText(null)),
        data);
  }

  /** 实体 ID 进程内缓存（先查缓存，miss 才回源实体列表接口）。 */
  private Long publishedEntityIdOf(String entityCode) throws Exception {
    Long cached = entityIdCache.get(entityCode);
    if (cached != null) {
      return cached;
    }
    Long entityId = findPublishedEntityId(entityCode);
    if (entityId != null) {
      entityIdCache.put(entityCode, entityId);
    }
    return entityId;
  }

  private static Optional<RecordView> matchIn(
      Snapshot snapshot, String bizKeyField, String bizKeyValue) {
    if (snapshot == null) {
      return Optional.empty();
    }
    return snapshot.records().stream()
        .filter(
            view ->
                // 一等公民业务主键命中 或（存量无 record_code）data JSON 里的业务键命中
                bizKeyValue.equals(view.recordCode())
                    || bizKeyValue.equals(stringValue(view.data(), bizKeyField)))
        .findFirst();
  }

  private Snapshot snapshotOf(String entityCode, boolean forceRefresh) throws Exception {
    long ttlMs = props.getCacheTtlSeconds() * 1000L;
    Snapshot cached = snapshotCache.get(entityCode);
    if (!forceRefresh
        && cached != null
        && Instant.now().toEpochMilli() - cached.fetchedAt() < ttlMs) {
      return cached;
    }
    // 强制刷新限流：避免"查一个不存在的编码"被放大成对 masterdata 的穿透打压
    // （同一个实体每秒最多回源一次，其余沿用旧快照——旧快照里查不到就如实返回"不存在"）。
    if (forceRefresh && cached != null) {
      Long last = forcedRefreshAt.get(entityCode);
      long now = Instant.now().toEpochMilli();
      if (last != null && now - last < FORCED_REFRESH_MIN_INTERVAL_MS) {
        return cached;
      }
      forcedRefreshAt.put(entityCode, now);
    }
    Long entityId = publishedEntityIdOf(entityCode);
    if (entityId == null) {
      log.warn("主数据实体未找到或未发布: entityCode={}", entityCode);
      return null;
    }
    List<RecordView> records = fetchPublishedRecords(entityId);
    Snapshot snapshot = new Snapshot(Instant.now().toEpochMilli(), records);
    snapshotCache.put(entityCode, snapshot);
    return snapshot;
  }

  /** 按实体编码搜主数据实体（关键字精确命中编码），返回其 ID；未发布/未找到返回 null。 */
  private Long findPublishedEntityId(String entityCode) throws Exception {
    JsonNode resp =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/masterdata/entities")
                        .queryParam("pageNum", 1)
                        .queryParam("pageSize", 50)
                        .queryParam("keyword", entityCode)
                        .build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    JsonNode records = resp == null ? null : resp.path("data").path("records");
    if (records == null || !records.isArray()) {
      return null;
    }
    for (JsonNode node : records) {
      // 实体列表 DTO 的编码字段是 entityCode（不是 code）
      if (entityCode.equals(node.path("entityCode").asText(null))
          && "PUBLISHED".equals(node.path("status").asText(""))) {
        return node.path("id").asLong();
      }
    }
    return null;
  }

  private List<RecordView> fetchPublishedRecords(Long entityId) throws Exception {
    JsonNode resp =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/masterdata/records")
                        .queryParam("pageNum", 1)
                        .queryParam("pageSize", props.getFetchPageSize())
                        .queryParam("masterDataEntityId", entityId)
                        .queryParam("status", "PUBLISHED")
                        .build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    List<RecordView> result = new ArrayList<>();
    JsonNode records = resp == null ? null : resp.path("data").path("records");
    if (records != null && records.isArray()) {
      for (JsonNode node : records) {
        result.add(toRecordView(node));
      }
    }
    return result;
  }

  private String token() throws Exception {
    if (cachedToken != null
        && Instant.now().getEpochSecond() - tokenFetchedAt.getEpochSecond() < TOKEN_TTL_SECONDS) {
      return cachedToken;
    }
    Map<String, String> loginBody = new HashMap<>();
    loginBody.put("username", props.getServiceUsername());
    loginBody.put("password", props.getServicePassword());
    JsonNode resp =
        restClient
            .post()
            .uri("/api/v1/iam/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(loginBody)
            .retrieve()
            .body(JsonNode.class);
    String token = resp == null ? null : resp.path("data").path("token").asText(null);
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("masterdata 服务登录失败：响应无 token");
    }
    cachedToken = token;
    tokenFetchedAt = Instant.now();
    return token;
  }

  private static String nullIfBlank(String v) {
    return v == null || v.isBlank() ? null : v;
  }

  private static String stringValue(Map<String, Object> data, String field) {
    Object v = data.get(field);
    return v == null ? null : String.valueOf(v).trim();
  }

  private Optional<BigDecimal> readDecimal(Map<String, Object> data, String field) {
    Object v = data.get(field);
    if (v == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new BigDecimal(String.valueOf(v).trim()));
    } catch (NumberFormatException e) {
      log.warn("主数据字段非数值: field={}, value={}", field, v);
      return Optional.empty();
    }
  }

  /** 已发布记录视图：业务主键（一等公民列）+ 属性数据。 */
  private record RecordView(String recordCode, String displayName, Map<String, Object> data) {}

  /** 已发布记录快照。 */
  private record Snapshot(long fetchedAt, List<RecordView> records) {}
}
