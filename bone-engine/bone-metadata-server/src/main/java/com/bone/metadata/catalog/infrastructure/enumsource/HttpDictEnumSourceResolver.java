package com.bone.metadata.catalog.infrastructure.enumsource;

import com.bone.metadata.catalog.domain.model.meta.OptionItem;
import com.bone.metadata.catalog.domain.service.EnumSourceResolver;
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
 * 字典选项源 ACL 适配器（防腐蚀层）：经网关 REST 调用 bone-system 的字典「下拉数据源」 {@code
 * /api/v1/system/dict/items/options}，并以服务账号经 IAM 登录取 Bearer token。
 *
 * <p><b>为何是 ACL 而不是直连库</b>：同构参照 bone-blueprint {@code DictAclAdapter}——字典是 bone-system 的
 * 内部存储，跨服务直读会把其表结构泄漏进 catalog。{@code /options} 是 bone-system 对外的官方防腐面 （合并平台+租户覆盖、 排除停用终态 DISABLED、按
 * lang 本地化标签），「全平台唯一取数契约」不裂变。三态口径（v1.1）：返 ACTIVE+DEPRECATED， 每项带 {@code
 * state}——物化把两态都写进快照（存量写放行），DEPRECATED 标注状态供渲染侧过滤与设计器展示。
 *
 * <p><b>缓存策略（cache-aside + stale-on-error，E2E 实证修正）</b>：读路径（设计器预览 / 漂移对比）
 * 常态<b>实时拉取</b>真源，字典任何变更（增删项、改标签）立刻可见——漂移徽标不再被 300s TTL 挡住 （旧实现把被动读也走 TTL 短路，导致 T8b/T8c
 * 漂移不可见）。缓存仅作为<b>宕机兜底</b>：真源不可达时 回退上一次成功结果（且不超过 {@code cacheTtlSeconds} 的陈旧上限），避免字典服务抖动把预览/漂移对比打挂。
 * 写路径（保存/发布物化/手动刷新）走强制档，实时失败<b>不回退</b>缓存——回退会让「发布即冻结」冻结成旧口径， 制造安静的正确性债务；不可达即 empty，由调用方 fail-closed
 * 抛 502。
 */
@Slf4j
@Component
public class HttpDictEnumSourceResolver implements EnumSourceResolver {

  private static final long TOKEN_TTL_SECONDS = 300;

  private final EnumSourceConsumptionProperties props;
  private final RestClient iamRestClient;
  private final RestClient systemRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** options 结果缓存：typeCode+lang → (抓取时间, 选项列表)。 */
  private final Map<String, CacheEntry> optionsCache = new ConcurrentHashMap<>();

  private volatile String cachedToken;
  private volatile long tokenFetchedAtEpochSecond = 0;

  public HttpDictEnumSourceResolver(EnumSourceConsumptionProperties props) {
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
  public Optional<List<OptionItem>> resolveDictOptions(String typeCode, String language) {
    // 被动读路径：实时优先 + 宕机兜底（cache-aside + stale-on-error）。漂移必须立即可见，
    // 因此常态不再走 TTL 短路；缓存只在本服务不可达时兜底返回上一次成功结果（且不超过陈旧上限）。
    return resolve(typeCode, language, true);
  }

  @Override
  public Optional<List<OptionItem>> resolveDictOptionsFresh(String typeCode, String language) {
    // 写路径（保存/发布物化/手动刷新）：实时优先 + fail-closed，绝不回退 stale 缓存——
    // 回退会让「发布即冻结」冻结成旧口径，制造安静的正确性债务。不可达即 empty，由调用方抛 502。
    return resolve(typeCode, language, false);
  }

  /**
   * 实时解析选项源。
   *
   * @param fallbackToCacheOnError true=被动读路径（实时失败回退 stale 缓存兜底）；false=写路径（实时失败直接 empty，由调用方
   *     fail-closed）
   */
  private Optional<List<OptionItem>> resolve(
      String typeCode, String language, boolean fallbackToCacheOnError) {
    if (typeCode == null || typeCode.isBlank()) {
      return Optional.empty();
    }
    String cacheKey = typeCode + "::" + (language == null ? "" : language);
    try {
      List<OptionItem> options = fetchOptions(typeCode, language);
      // cache-aside：成功即回填缓存，缓存永远是「上一次成功」的副本，TTL 不再挡读路径
      optionsCache.put(cacheKey, new CacheEntry(System.currentTimeMillis(), options));
      return Optional.of(options);
    } catch (Exception e) {
      log.warn(
          "字典 options 实时查询失败（cache-aside fallback={}）: typeCode={}, lang={}",
          fallbackToCacheOnError,
          typeCode,
          language,
          e);
      if (fallbackToCacheOnError) {
        CacheEntry cached = optionsCache.get(cacheKey);
        if (cached != null) {
          long ageSeconds = (System.currentTimeMillis() - cached.fetchedAtMillis()) / 1000L;
          if (ageSeconds <= props.getCacheTtlSeconds()) {
            log.warn("返回 stale 缓存兜底（真实宕机，非漂移）: typeCode={}, ageSeconds={}", typeCode, ageSeconds);
            return Optional.of(cached.options());
          }
          log.warn(
              "stale 缓存过旧（{}s > {}s），放弃兜底按不可达处理: typeCode={}",
              ageSeconds,
              props.getCacheTtlSeconds(),
              typeCode);
        }
      }
      return Optional.empty();
    }
  }

  private List<OptionItem> fetchOptions(String typeCode, String language) throws Exception {
    List<OptionItem> options = fetchOptionsRaw(typeCode, language);
    // 空结果二义性消解（S8 悬空绑定拒绝的执行点）：
    // /options 对不存在的 typeCode 同样返空列表而非报错（DictApplicationService.options 只按
    // typeCode 查项，查不到即空）。若不补类型存在性探测，绑错一个 code 会被静默物化成空码表——
    // 「绑定成功但选项为空」正是本方案要消灭的半吊子状态。探针：GET /dict/types/{code}，
    // data 为 null 即类型不存在 ⇒ 抛异常按不可达处理，写路径 fail-closed 拒绝保存。
    // 类型存在但项全部停用 ⇒ 可达的空值域（真实中间态，物化为空码表，漂移机制负责后续提示）。
    if (options.isEmpty() && !typeExists(typeCode)) {
      throw new IllegalStateException("字典类型不存在: " + typeCode);
    }
    return options;
  }

  private List<OptionItem> fetchOptionsRaw(String typeCode, String language) throws Exception {
    JsonNode resp =
        systemRestClient
            .get()
            .uri(
                uriBuilder -> {
                  uriBuilder.path("/api/v1/system/dict/items/options").queryParam("type", typeCode);
                  if (language != null && !language.isBlank()) {
                    uriBuilder.queryParam("lang", language);
                  }
                  return uriBuilder.build();
                })
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    JsonNode data = resp == null ? null : resp.path("data");
    List<OptionItem> result = new ArrayList<>();
    if (data != null && data.isArray()) {
      for (JsonNode node : data) {
        String code = nullIfBlank(node.path("code").asText(null));
        String label = nullIfBlank(node.path("label").asText(null));
        if (code != null) {
          // 三态口径（v1.1 §4.2）：/options 返 ACTIVE+DEPRECATED，DISABLED 不返；
          // state 缺省按 ACTIVE 兼容存量响应。物化据此写快照，渲染侧由前端按 ACTIVE 过滤。
          String state = nullIfBlank(node.path("state").asText(null));
          result.add(
              new OptionItem(code, label != null ? label : code, state != null ? state : "ACTIVE"));
        }
      }
    }
    return result;
  }

  /** 类型存在性探针：{@code GET /dict/types/{code}}，不存在时该端点返 {@code data:null}（不抛 404）。 */
  private boolean typeExists(String typeCode) throws Exception {
    JsonNode resp =
        systemRestClient
            .get()
            .uri("/api/v1/system/dict/types/{code}", typeCode)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
    JsonNode data = resp == null ? null : resp.path("data");
    return data != null && data.isObject();
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
      throw new IllegalStateException("选项源消费登录 IAM 失败：响应无 token");
    }
    cachedToken = token;
    tokenFetchedAtEpochSecond = nowSec;
    return token;
  }

  private static String nullIfBlank(String v) {
    return v == null || v.isBlank() ? null : v;
  }

  private record CacheEntry(long fetchedAtMillis, List<OptionItem> options) {}
}
