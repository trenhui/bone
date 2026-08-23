package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Elasticsearch 连接器：基于 ES REST API 的真实调用（零依赖，JDK HttpClient）。 */
@Component("ELASTICSEARCH")
public class EsClientImpl implements ExternalSystemClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @Override
  public boolean testConnection(Map<String, Object> config) {
    String base = baseUrl(config);
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(base)).timeout(TIMEOUT).GET().build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() >= 200 && response.statusCode() < 400;
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    String base = baseUrl(config);
    String index = indexOf(params, config);
    String operation = operationOf(params, config);
    String uri = base + "/" + index + operationPath(operation, params);
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri)).timeout(TIMEOUT);
    switch (operation.toUpperCase()) {
      case "INDEX" -> builder.POST(bodyPublisher(params));
      case "DELETE" -> builder.DELETE();
      default -> builder.GET(); // SEARCH / GET
    }
    try {
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("statusCode", response.statusCode());
      result.put("body", response.body());
      return result;
    } catch (Exception ex) {
      throw new IllegalStateException("ES 请求失败: " + ex.getMessage(), ex);
    }
  }

  @Override
  public String getType() {
    return "ELASTICSEARCH";
  }

  private static String baseUrl(Map<String, Object> config) {
    String url = config.get("url") == null ? null : String.valueOf(config.get("url"));
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("ES URL 不能为空");
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String indexOf(Map<String, Object> params, Map<String, Object> config) {
    Object idx = params != null ? params.get("index") : null;
    if (idx == null) {
      idx = config.get("index");
    }
    return idx == null ? "_all" : String.valueOf(idx);
  }

  private static String operationOf(Map<String, Object> params, Map<String, Object> config) {
    Object op = params != null ? params.get("operation") : null;
    if (op == null) {
      op = config.get("operation");
    }
    return op == null ? "SEARCH" : String.valueOf(op);
  }

  private static String operationPath(String operation, Map<String, Object> params) {
    String id =
        params != null && params.get("id") != null ? String.valueOf(params.get("id")) : null;
    return switch (operation.toUpperCase()) {
      case "INDEX", "DELETE" -> id != null ? "/_doc/" + id : "/_doc";
      default -> "/_search";
    };
  }

  private static HttpRequest.BodyPublisher bodyPublisher(Map<String, Object> params) {
    Object body = params != null ? params.get("body") : null;
    if (body == null) {
      return HttpRequest.BodyPublishers.noBody();
    }
    if (body instanceof String s) {
      return HttpRequest.BodyPublishers.ofString(s);
    }
    try {
      return HttpRequest.BodyPublishers.ofString(
          new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body));
    } catch (Exception ex) {
      return HttpRequest.BodyPublishers.ofString(String.valueOf(body));
    }
  }
}
