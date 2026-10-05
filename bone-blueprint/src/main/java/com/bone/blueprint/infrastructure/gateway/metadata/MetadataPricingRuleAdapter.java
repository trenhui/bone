package com.bone.blueprint.infrastructure.gateway.metadata;

import com.bone.blueprint.domain.gateway.PricingRuleGateway;
import com.bone.blueprint.infrastructure.config.PricingRuleConsumptionProperties;
import com.bone.core.tenant.context.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 定价规则 ACL 适配器（防腐蚀层）：通过网关 REST 调用 bone-metadata-server 的模式 B 动态记录 API。
 *
 * <p><b>查询模型</b>：拉取 PRICING_RULE 实体（须已发布 RUNTIME）的全部记录，内存按 scenario 字段匹配，
 * 取启用规则中的最大折扣率（多规则并存时取对客户最优惠者，避免运营误配多行导致行为不确定）。样板工程数据量 小（单实体数百条内），整拉 + 内存匹配足够；数据量上来后应升级为按 scenario
 * 的点查契约。
 *
 * <p><b>多租户</b>：以当前请求租户（{@link TenantContext}）透传 {@code X-Tenant-Id}——定价规则按租户隔离， 与元数据服务的租户内聚一致；服务账号
 * token 仅用于通过网关认证。
 *
 * <p><b>降级</b>：登录/查询任一环节失败按「不可达」处理——返回 empty 并 WARN，结果带 TTL 缓存避免规则中心 抖动放大成下单失败。
 */
@Slf4j
@Component
public class MetadataPricingRuleAdapter implements PricingRuleGateway {

  private static final long TOKEN_TTL_SECONDS = 300;

  private final PricingRuleConsumptionProperties props;
  private final RestClient restClient;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      new com.fasterxml.jackson.databind.ObjectMapper();

  /** token 缓存：值同时记获取时间，过期重登。 */
  private volatile String cachedToken;

  private volatile Instant tokenFetchedAt = Instant.EPOCH;

  /** 记录快照缓存：tenantId → (抓取时间, 记录字段列表)。 */
  private final Map<Long, Snapshot> snapshotCache = new ConcurrentHashMap<>();

  public MetadataPricingRuleAdapter(PricingRuleConsumptionProperties props) {
    this.props = props;
    this.restClient =
        RestClient.builder()
            .baseUrl(props.getBaseUrl())
            .requestFactory(
                new SimpleClientHttpRequestFactory() {
                  {
                    setConnectTimeout(2000);
                    setReadTimeout(4000);
                  }
                })
            .build();
  }

  @Override
  public Optional<BigDecimal> findScenarioDiscountRate(String scenario) {
    if (!props.isEnabled() || scenario == null || scenario.isBlank()) {
      return Optional.empty();
    }
    try {
      Long tenantId = TenantContext.getTenantIdAsLong();
      List<Map<String, Object>> records = snapshotOf(tenantId);
      BigDecimal best = null;
      for (Map<String, Object> row : records) {
        if (!scenario.equalsIgnoreCase(stringValue(row, props.getScenarioField()))) {
          continue;
        }
        if (row.containsKey(props.getEnabledField())
            && !"true".equalsIgnoreCase(stringValue(row, props.getEnabledField()))
            && !"1".equals(stringValue(row, props.getEnabledField()))) {
          continue;
        }
        Optional<BigDecimal> rate = readDecimal(row, props.getRateField());
        if (rate.isEmpty()
            || rate.get().compareTo(BigDecimal.ZERO) <= 0
            || rate.get().compareTo(BigDecimal.ONE) > 0) {
          continue;
        }
        if (best == null || rate.get().compareTo(best) < 0) {
          best = rate.get();
        }
      }
      return Optional.ofNullable(best);
    } catch (Exception e) {
      log.warn("定价规则中心查询失败（按不可达降级）: scenario={}", scenario, e);
      return Optional.empty();
    }
  }

  // ===================== 内部：记录快照 =====================

  private List<Map<String, Object>> snapshotOf(Long tenantId) throws Exception {
    long ttlMs = props.getCacheTtlSeconds() * 1000L;
    Snapshot cached = snapshotCache.get(tenantId);
    if (cached != null && Instant.now().toEpochMilli() - cached.fetchedAt() < ttlMs) {
      return cached.records();
    }
    JsonNode resp =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/runtime/entities/{code}/records")
                        .queryParam("page", 1)
                        .queryParam("size", props.getFetchSize())
                        .build(props.getEntityCode()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .header("X-Tenant-Id", String.valueOf(tenantId))
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    List<Map<String, Object>> result = new ArrayList<>();
    JsonNode records = resp == null ? null : resp.path("data").path("records");
    if (records != null && records.isArray()) {
      for (JsonNode node : records) {
        // 兼容两种行形态：{id, data:{field...}} 与 {field...}（扁平）
        JsonNode dataNode = node.path("data");
        Map<String, Object> row =
            dataNode.isObject()
                ? objectMapper.convertValue(dataNode, Map.class)
                : objectMapper.convertValue(node, Map.class);
        result.add(row);
      }
    }
    snapshotCache.put(tenantId, new Snapshot(Instant.now().toEpochMilli(), result));
    log.debug("定价规则快照已加载: tenantId={}, records={}", tenantId, result.size());
    return result;
  }

  private String token() throws Exception {
    if (cachedToken != null
        && Instant.now().getEpochSecond() - tokenFetchedAt.getEpochSecond() < TOKEN_TTL_SECONDS) {
      return cachedToken;
    }
    java.util.Map<String, String> loginBody = new java.util.HashMap<>();
    loginBody.put("username", props.getServiceUsername());
    loginBody.put("password", props.getServicePassword());
    JsonNode resp =
        restClient
            .post()
            .uri("/api/v1/iam/login")
            .header("X-Tenant-Id", String.valueOf(props.getServiceTenantId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(loginBody)
            .retrieve()
            .body(JsonNode.class);
    String token = resp == null ? null : resp.path("data").path("token").asText(null);
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("定价规则中心登录失败：响应无 token");
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
      log.warn("定价规则字段非数值: field={}, value={}", field, v);
      return Optional.empty();
    }
  }

  /** 已发布记录快照。 */
  private record Snapshot(long fetchedAt, List<Map<String, Object>> records) {}
}
