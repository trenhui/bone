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
 * <p><b>查询模型</b>：按建模约定的实体编码搜实体 → 拉该实体全部「已发布」记录 → 在内存按记录 data 的 业务键（商品编码 /
 * 等级编码）匹配。样板工程数据量小（单实体数百条内），整拉 + 内存匹配足够； 数据量上来后应升级为 masterdata 提供按业务键的点查契约。
 *
 * <p><b>降级</b>：登录/查询任一环节失败按「不可达」处理——返回 false/empty 并 WARN（见端口约定）， 结果带 TTL 缓存避免主数据抖动放大成下单失败。
 */
@Slf4j
@Component
public class MasterDataAclAdapter implements MasterDataGateway {

  private static final long TOKEN_TTL_SECONDS = 300;

  private final MasterDataConsumptionProperties props;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** token 缓存：值同时记获取时间，过期重登。 */
  private volatile String cachedToken;

  private volatile Instant tokenFetchedAt = Instant.EPOCH;

  /** 已发布记录快照缓存：实体编码 → (抓取时间, 记录 data 列表)。 */
  private final Map<String, Snapshot> snapshotCache = new ConcurrentHashMap<>();

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
              data -> {
                String code = stringValue(data, "code");
                String name = stringValue(data, "name");
                Optional<BigDecimal> price = readDecimal(data, "price");
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
          .map(data -> stringValue(data, "level_code"))
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
        .flatMap(data -> readDecimal(data, "discount_rate"))
        .filter(rate -> rate.compareTo(BigDecimal.ZERO) > 0 && rate.compareTo(BigDecimal.ONE) <= 0);
  }

  // ===================== 内部：实体定位 + 记录快照 =====================

  private Optional<Map<String, Object>> findPublishedRecord(
      String entityCode, String bizKeyField, String bizKeyValue) {
    try {
      Snapshot snapshot = snapshotOf(entityCode);
      if (snapshot == null) {
        return Optional.empty();
      }
      return snapshot.records().stream()
          .filter(data -> bizKeyValue.equals(stringValue(data, bizKeyField)))
          .findFirst();
    } catch (Exception e) {
      log.warn("主数据查询失败（按不可达降级）: entityCode={}, key={}", entityCode, bizKeyValue, e);
      return Optional.empty();
    }
  }

  private Snapshot snapshotOf(String entityCode) throws Exception {
    long ttlMs = props.getCacheTtlSeconds() * 1000L;
    Snapshot cached = snapshotCache.get(entityCode);
    if (cached != null && Instant.now().toEpochMilli() - cached.fetchedAt() < ttlMs) {
      return cached;
    }
    Long entityId = findPublishedEntityId(entityCode);
    if (entityId == null) {
      log.warn("主数据实体未找到或未发布: entityCode={}", entityCode);
      return null;
    }
    List<Map<String, Object>> records = fetchPublishedRecords(entityId);
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

  private List<Map<String, Object>> fetchPublishedRecords(Long entityId) throws Exception {
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
    List<Map<String, Object>> result = new ArrayList<>();
    JsonNode records = resp == null ? null : resp.path("data").path("records");
    if (records != null && records.isArray()) {
      for (JsonNode node : records) {
        JsonNode dataNode = node.path("data");
        Map<String, Object> data =
            dataNode.isTextual()
                ? objectMapper.readValue(dataNode.asText(), Map.class)
                : objectMapper.convertValue(dataNode, Map.class);
        result.add(data);
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

  private String stringValue(Map<String, Object> data, String field) {
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

  /** 已发布记录快照。 */
  private record Snapshot(long fetchedAt, List<Map<String, Object>> records) {}
}
