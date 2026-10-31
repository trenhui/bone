package com.bone.workflow.starter.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 经 IAM 的 {@code GET /api/v1/iam/accounts/{id}/org-hierarchy} 取组织位置。
 *
 * <p><b>为什么用 JDK 的 {@link HttpClient} 而不是 RestClient/WebClient</b>：starter 的 {@code
 * spring-boot-starter-web} 是 optional 依赖（宿主可以不是 web 应用），而审批引擎本身是纯领域逻辑。为了一个 GET 请求把 web
 * 运行时变成硬依赖，等于把这个可选性废掉；JDK 客户端零依赖、零传递，也不会与宿主的 HTTP 客户端配置互相干扰。
 *
 * <p><b>失败一律抛异常，不返回 empty</b>：见 {@link OrgHierarchyQuery} 的契约说明——「IAM 不可达」和
 * 「这个人没有主管」是两件必须区分的事，前者要让运维看见。
 */
public class HttpOrgHierarchyQuery implements OrgHierarchyQuery {

  private static final Logger log = LoggerFactory.getLogger(HttpOrgHierarchyQuery.class);

  /** 服务间 token 复用窗口：与 metadata-server 的字典 ACL 同口径，避免每查一个主管就登一次录。 */
  private static final long TOKEN_TTL_SECONDS = 300;

  private final OrgDirectoryProperties props;
  private final HttpClient http;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private volatile String cachedToken;
  private volatile long tokenFetchedAtEpochSecond;

  public HttpOrgHierarchyQuery(OrgDirectoryProperties props) {
    this.props = props;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMillis()))
            .build();
  }

  @Override
  public Optional<OrgHierarchy> byAccountId(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return Optional.empty();
    }
    try {
      JsonNode resp =
          send(
              HttpRequest.newBuilder()
                  .uri(
                      URI.create(
                          trimTrailingSlash(props.getIamBaseUrl())
                              + "/api/v1/iam/accounts/"
                              + accountId
                              + "/org-hierarchy"))
                  .timeout(Duration.ofMillis(props.getReadTimeoutMillis()))
                  .header("Authorization", "Bearer " + token())
                  .header("Accept", "application/json")
                  .GET());
      JsonNode data = resp.path("data");
      if (data.isMissingNode() || data.isNull()) {
        // 账号不存在时 IAM 返 404，RestClient 风格下会被异常路径接住；这里兜住 code!=200 的软失败。
        return Optional.empty();
      }
      return Optional.of(
          new OrgHierarchy(
              text(data, "accountId"),
              text(data, "deptId"),
              text(data, "lineManagerId"),
              text(data, "deptHeadId")));
    } catch (Exception e) {
      throw new IllegalStateException("查询组织层级失败: accountId=" + accountId, e);
    }
  }

  /**
   * 取服务间 token（缓存 {@value #TOKEN_TTL_SECONDS} 秒）。
   *
   * <p>token 失效时不主动刷新重试：审批是低频路径，下一次调用自然会走到这里重新登录；为一次失败加一轮重试反而 让「IAM 真挂了」的故障被延迟暴露。
   */
  private String token() throws Exception {
    long nowSec = System.currentTimeMillis() / 1000L;
    if (cachedToken != null && nowSec - tokenFetchedAtEpochSecond < TOKEN_TTL_SECONDS) {
      return cachedToken;
    }
    Map<String, String> body = new HashMap<>();
    body.put("username", props.getServiceUsername());
    body.put("password", props.getServicePassword());
    JsonNode resp =
        send(
            HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(props.getIamBaseUrl()) + "/api/v1/iam/login"))
                .timeout(Duration.ofMillis(props.getReadTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)));
    String token = resp.path("data").path("token").asText(null);
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("组织层级查询登录 IAM 失败：响应无 token");
    }
    cachedToken = token;
    tokenFetchedAtEpochSecond = nowSec;
    return token;
  }

  private JsonNode send(HttpRequest.Builder builder) throws Exception {
    HttpResponse<String> resp =
        http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (resp.statusCode() / 100 != 2) {
      log.warn("IAM 组织层级接口返回 {}：{}", resp.statusCode(), resp.body());
      throw new IllegalStateException("IAM 返回 " + resp.statusCode());
    }
    return objectMapper.readTree(resp.body());
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private static String trimTrailingSlash(String url) {
    return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
