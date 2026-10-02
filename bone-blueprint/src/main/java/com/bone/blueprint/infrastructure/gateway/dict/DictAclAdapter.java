package com.bone.blueprint.infrastructure.gateway.dict;

import com.bone.blueprint.domain.gateway.DictGateway;
import com.bone.blueprint.infrastructure.config.DictConsumptionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 字典消费 ACL 适配器（防腐蚀层）：通过网关 REST 调用 bone-system 的字典「下拉数据源」 {@code
 * /api/v1/system/dict/items/options}，并以服务账号经 IAM 登录取 Bearer token（与 MasterDataAclAdapter 同范式）。
 *
 * <p><b>为何是 ACL 而不是直连库</b>：字典是 bone-system 的内部存储，跨服务直读会把其表结构泄漏进 blueprint， 对方一改字典模型蓝图就炸裂。{@code
 * /options} 是 bone-system 对外的官方防腐面（合并平台+租户覆盖、过滤停用与未生效、按 lang 本地化标签）。
 *
 * <p><b>降级</b>：登录/查询任一环节失败按「不可达」处理——返回 {@link Optional#empty()} 并 WARN， 结果带 TTL
 * 缓存避免字典服务抖动放大成下单失败。这与领域端口 {@link DictGateway} 的降级约定一致：写路径放行（不阻断下单）、读路径标签置空。
 */
@Slf4j
@Component
public class DictAclAdapter implements DictGateway {

  private static final long TOKEN_TTL_SECONDS = 300;

  private final DictConsumptionProperties props;
  private final RestClient iamRestClient;
  private final RestClient systemRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** options 结果缓存：typeCode → (抓取时间, 选项列表)。字典是「几乎不变」的配置，缓存友好。 */
  private final Map<String, CacheEntry> optionsCache = new ConcurrentHashMap<>();

  private volatile String cachedToken;
  private volatile long tokenFetchedAtEpochSecond = 0;

  public DictAclAdapter(DictConsumptionProperties props) {
    this.props = props;
    this.iamRestClient = buildClient(props.getIamBaseUrl());
    this.systemRestClient = buildClient(props.getSystemBaseUrl());
  }

  private static RestClient buildClient(String baseUrl) {
    return RestClient.builder()
        .baseUrl(baseUrl)
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
  public Optional<List<DictOptionView>> listOptions(String typeCode) {
    if (typeCode == null || typeCode.isBlank()) {
      return Optional.empty();
    }
    try {
      CacheEntry cached = optionsCache.get(typeCode);
      long now = System.currentTimeMillis();
      if (cached != null && now - cached.fetchedAtMillis() < props.getCacheTtlSeconds() * 1000L) {
        return Optional.of(cached.options());
      }
      List<DictOptionView> options = fetchOptions(typeCode);
      optionsCache.put(typeCode, new CacheEntry(now, options));
      return Optional.of(options);
    } catch (Exception e) {
      log.warn("字典 options 查询失败（按不可达降级）: typeCode={}", typeCode, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> resolveLabel(String typeCode, String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return listOptions(typeCode)
        .flatMap(
            options ->
                options.stream()
                    .filter(o -> o.code().equals(code))
                    .map(DictOptionView::label)
                    .findFirst());
  }

  private List<DictOptionView> fetchOptions(String typeCode) throws Exception {
    JsonNode resp =
        systemRestClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/system/dict/items/options")
                        .queryParam("type", typeCode)
                        .build())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    JsonNode data = resp == null ? null : resp.path("data");
    List<DictOptionView> result = new ArrayList<>();
    if (data != null && data.isArray()) {
      for (JsonNode node : data) {
        String code = nullIfBlank(node.path("code").asText(null));
        String label = nullIfBlank(node.path("label").asText(null));
        if (code != null) {
          result.add(new DictOptionView(code, label));
        }
      }
    }
    return result;
  }

  private String token() throws Exception {
    long nowSec = System.currentTimeMillis() / 1000L;
    if (cachedToken != null && nowSec - tokenFetchedAtEpochSecond < TOKEN_TTL_SECONDS) {
      return cachedToken;
    }
    Map<String, String> loginBody = new HashMap<>();
    loginBody.put("username", props.getServiceUsername());
    loginBody.put("password", props.getServicePassword());
    JsonNode resp =
        iamRestClient
            .post()
            .uri("/api/v1/iam/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(loginBody)
            .retrieve()
            .body(JsonNode.class);
    String token = resp == null ? null : resp.path("data").path("token").asText(null);
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("字典消费登录 IAM 失败：响应无 token");
    }
    cachedToken = token;
    tokenFetchedAtEpochSecond = nowSec;
    return token;
  }

  private static String nullIfBlank(String v) {
    return v == null || v.isBlank() ? null : v;
  }

  private record CacheEntry(long fetchedAtMillis, List<DictOptionView> options) {}
}
