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

/** SOAP 连接器：基于 JDK HttpClient 发送 SOAP 信封 XML（零依赖）。 */
@Component("SOAP")
public class SoapClientImpl implements ExternalSystemClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String SOAP_CONTENT_TYPE = "text/xml; charset=utf-8";
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @Override
  public boolean testConnection(Map<String, Object> config) {
    String endpoint = endpointOf(config);
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(endpoint)).timeout(TIMEOUT).GET().build();
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
    String target = endpointOf(config);
    String envelope = buildEnvelope(params, config);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(target))
            .timeout(TIMEOUT)
            .header("Content-Type", SOAP_CONTENT_TYPE)
            .header("SOAPAction", soapActionOf(params, config))
            .POST(HttpRequest.BodyPublishers.ofString(envelope))
            .build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("statusCode", response.statusCode());
      result.put("body", response.body());
      return result;
    } catch (Exception ex) {
      throw new IllegalStateException("SOAP 请求失败: " + ex.getMessage(), ex);
    }
  }

  @Override
  public String getType() {
    return "SOAP";
  }

  private static String endpointOf(Map<String, Object> config) {
    String url = config.get("url") == null ? null : String.valueOf(config.get("url"));
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("SOAP endpoint URL 不能为空");
    }
    return url;
  }

  private static String soapActionOf(Map<String, Object> params, Map<String, Object> config) {
    Object action = params != null ? params.get("action") : null;
    if (action == null) {
      action = config.get("action");
    }
    return action == null ? "" : String.valueOf(action);
  }

  private static String buildEnvelope(Map<String, Object> params, Map<String, Object> config) {
    Object body = params != null ? params.get("body") : null;
    if (body == null) {
      body = config.get("body");
    }
    String operation =
        params != null && params.get("operation") != null
            ? String.valueOf(params.get("operation"))
            : "execute";
    String inner = body == null ? "" : String.valueOf(body);
    return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soapenv:Body><"
        + operation
        + ">"
        + inner
        + "</"
        + operation
        + "></soapenv:Body></soapenv:Envelope>";
  }
}
