package com.bone.blueprint.infrastructure.channel.openapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

  /**
   * 缓存的客户端快照。
   *
   * <p>{@link HttpClient} 线程安全，但「重建」这一步要与并发请求协调：用 {@link AtomicReference#compareAndSet} 让
   * 只有一个线程真正替换，其余线程继续用旧实例，避免把在途请求打断。
   */
  private final AtomicReference<ClientHolder> cached = new AtomicReference<>();

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

  /**
   * 取可用的 {@link HttpClient}：命中缓存直接复用，键变化时重建。
   *
   * <p><b>为什么必须缓存（2026-10-06 修正）</b>：早期实现每次请求都 {@code HttpClient.newBuilder()...build()}，
   * 注释写着「按超时缓存」但代码里<b>没有任何缓存</b>。{@code HttpClient} 内部持有连接池与 selector 线程池， 每次新建等于每次重建连接池 ⇒ 高并发下
   * TCP/TLS 握手开销被放大 N 倍，TIME_WAIT 堆积、 偶发「connect timed out」。改为按「连接超时 + 重定向开关」缓存实例，配置变更时重建。
   *
   * <p>缓存键<b>只含连接层参数</b>：读超时是每请求设在 {@link HttpRequest} 上的，不影响连接池，进键只会造成无谓重建。
   */
  private HttpClient client() {
    int key = clientKey();
    ClientHolder holder = cached.get();
    if (holder != null && holder.key() == key) {
      return holder.client();
    }
    HttpClient rebuilt =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .followRedirects(
                properties.isFollowRedirects()
                    ? HttpClient.Redirect.NORMAL
                    : HttpClient.Redirect.NEVER)
            .build();
    // CAS：并发下只有一个线程真正替换，其余线程继续用旧实例（不打断在途请求）
    if (cached.compareAndSet(holder, new ClientHolder(rebuilt, key))) {
      log.debug("渠道 HTTP 客户端已重建（连接配置变更）: connectTimeoutMs={}", properties.getConnectTimeoutMs());
    }
    return rebuilt;
  }

  /** 客户端缓存键：只纳入影响连接行为的参数。 */
  private int clientKey() {
    return properties.getConnectTimeoutMs() * 31 + (properties.isFollowRedirects() ? 1 : 0);
  }

  /** 客户端快照：{@link HttpClient} 与其缓存键。用不可变 record 而非可变字段，便于 CAS 替换。 */
  private record ClientHolder(HttpClient client, int key) {}
}
