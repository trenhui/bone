package com.bone.blueprint.infrastructure.channel.openapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 JDK {@code java.net.http.HttpClient} 的渠道传输实现。
 *
 * <p><b>为什么不再引第三方 HTTP 客户端</b>：渠道网关只需要 form 编码 POST， 引 OkHttp / RestTemplate 会为「一个 POST」
 * 多带一棵依赖树，还得处理连接池与超时策略的重复配置。JDK 自带客户端足够，且由调用方按渠道超时参数构造。
 *
 * <p><b>超时为什么由调用方注入</b>：不同渠道响应速度差别很大（抖音电商明显慢于拼多多），把超时写死成常量会导致慢渠道大面积超时、 快渠道白等；由客户端按 {@link
 * ChannelOpenApiProperties} 分别注入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdkChannelHttpExecutor implements ChannelHttpExecutor {

  private final ChannelOpenApiProperties properties;

  @Override
  public ChannelHttpResponse post(
      String url, Map<String, String> headers, Map<String, String> form) {
    boolean emptyBody = form == null || form.isEmpty();
    String body =
        emptyBody
            ? ""
            : form.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (headers != null) {
      headers.forEach(request::header);
    }
    try {
      HttpResponse<String> response =
          client()
              .send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new ChannelHttpResponse(response.statusCode(), response.body());
    } catch (IOException ex) {
      throw new ChannelHttpTransportException("调用渠道网关失败: " + url, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ChannelHttpTransportException("调用渠道网关被中断: " + url, ex);
    }
  }

  /** 客户端实例按超时缓存：每次调用都 new 一个会丢连接池复用。 */
  private HttpClient client() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }
}
