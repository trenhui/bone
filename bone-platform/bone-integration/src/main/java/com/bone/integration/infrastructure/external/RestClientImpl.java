package com.bone.integration.infrastructure.external;

import com.bone.integration.domain.client.ExternalSystemClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** REST/HTTP 连接器：基于 {@link HttpClient} 的真实调用（INT-08）。 */
@Component("REST")
public class RestClientImpl implements ExternalSystemClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @Override
  public boolean testConnection(Map<String, Object> config) {
    String url = requireBaseUrl(config);
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
    try {
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      int code = response.statusCode();
      return code >= 200 && code < 400;
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public Object sendRequest(
      String endpoint, Map<String, Object> params, Map<String, Object> config) {
    String baseUrl = requireBaseUrl(config);
    String method = resolveMethod(params, config);
    URI uri = resolveUri(baseUrl, endpoint, params);

    HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(TIMEOUT);
    String body = resolveBody(params, config);
    switch (method) {
      case "POST" -> builder.POST(bodyPublisher(body));
      case "PUT" -> builder.PUT(bodyPublisher(body));
      case "PATCH" -> builder.method("PATCH", bodyPublisher(body));
      case "DELETE" -> builder.DELETE();
      default -> builder.GET();
    }

    try {
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("statusCode", response.statusCode());
      result.put("body", response.body());
      return result;
    } catch (Exception ex) {
      throw new IllegalStateException("REST 请求失败: " + ex.getMessage(), ex);
    }
  }

  @Override
  public String getType() {
    return "REST";
  }

  private static String requireBaseUrl(Map<String, Object> config) {
    String url = stringVal(config.get("url"));
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("URL不能为空");
    }
    return url;
  }

  private static String resolveMethod(Map<String, Object> params, Map<String, Object> config) {
    String method = stringVal(params != null ? params.get("method") : null);
    if (method == null) {
      method = stringVal(config.get("method"));
    }
    return method != null ? method.toUpperCase() : "GET";
  }

  private static URI resolveUri(String baseUrl, String endpoint, Map<String, Object> params) {
    String path = endpoint;
    if (path == null || path.isBlank()) {
      path = stringVal(params != null ? params.get("path") : null);
    }
    if (path == null || path.isBlank()) {
      return URI.create(baseUrl);
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
      return URI.create(path);
    }
    String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    String rel = path.startsWith("/") ? path.substring(1) : path;
    return URI.create(base + rel);
  }

  private static String resolveBody(Map<String, Object> params, Map<String, Object> config) {
    if (params != null && params.get("body") != null) {
      return stringify(params.get("body"));
    }
    if (config.get("body") != null) {
      return stringify(config.get("body"));
    }
    return null;
  }

  private static HttpRequest.BodyPublisher bodyPublisher(String body) {
    if (body == null || body.isBlank()) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofString(body);
  }

  private static String stringify(Object value) {
    if (value instanceof String s) {
      return s;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      return String.valueOf(value);
    }
  }

  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
