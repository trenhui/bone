package com.bone.ai.gateway.infrastructure;

import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.infrastructure.ResilientLlmTransport.RetriableHttpException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JDK 原生 HTTP 出站传输（零依赖）：同时实现 {@code post}（缓冲）与 {@code streamPost}（SSE 真流式）。
 *
 * <p>替代原 auto-config 内联 lambda——lambda 无法实现两个方法，而流式（2026 LLM 网关标配，首 token 延迟 / 长文逐步呈现 / 背压）要求
 * {@code streamPost} 真实落地。熔断 / 重试仍由 {@link ResilientLlmTransport} 在外层包裹（其 {@code streamPost}
 * 透传本实现，断路器守卫流获取；流的持续消费不计入断路器，与业界「熔断防重试风暴、不误伤正常长流」一致）。
 */
public class JdkLlmTransport implements LlmHttpTransport {

  private final HttpClient http;
  private final Duration timeout;

  public JdkLlmTransport(LlmGatewayProperties props) {
    this.timeout = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(30);
    this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public String post(String url, Map<String, String> headers, String jsonBody) {
    HttpResponse<String> resp;
    try {
      resp = http.send(buildRequest(url, headers, jsonBody), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new RetriableHttpException("LLM 调用异常: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetriableHttpException("LLM 调用被中断: " + e.getMessage(), e);
    }
    if (resp.statusCode() >= 500) {
      throw new RetriableHttpException("LLM 服务端错误: HTTP " + resp.statusCode(), null);
    }
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new IllegalStateException("LLM 调用失败: HTTP " + resp.statusCode());
    }
    return resp.body();
  }

  @Override
  public Stream<String> streamPost(String url, Map<String, String> headers, String jsonBody) {
    HttpResponse<InputStream> resp;
    try {
      resp =
          http.send(
              buildRequest(url, headers, jsonBody), HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw new RetriableHttpException("LLM 流式调用异常: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetriableHttpException("LLM 流式调用被中断: " + e.getMessage(), e);
    }
    if (resp.statusCode() >= 500) {
      throw new RetriableHttpException("LLM 流式服务端错误: HTTP " + resp.statusCode(), null);
    }
    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
      throw new IllegalStateException("LLM 流式调用失败: HTTP " + resp.statusCode());
    }
    // 按行读 SSE；仅取 data: 载荷、剔除 [DONE]。流被消费完（读到 null）时自动关 reader，避免连接泄漏。
    InputStream in = resp.body();
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    Iterator<String> lineIter =
        new Iterator<>() {
          private String next = null;
          private boolean done = false;

          @Override
          public boolean hasNext() {
            if (done) {
              return false;
            }
            try {
              next = reader.readLine();
              if (next == null) {
                done = true;
                closeQuietly();
                return false;
              }
              return true;
            } catch (IOException e) {
              done = true;
              closeQuietly();
              return false;
            }
          }

          @Override
          public String next() {
            return next;
          }

          private void closeQuietly() {
            try {
              reader.close();
            } catch (IOException ignore) {
              // 关闭失败不影响已读到的流内容
            }
          }
        };
    Stream<String> lines =
        StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(lineIter, Spliterator.ORDERED), false);
    return lines
        .filter(line -> line.startsWith("data:"))
        .map(line -> line.substring(5).strip())
        .filter(payload -> !payload.isEmpty() && !"[DONE]".equals(payload));
  }

  private HttpRequest buildRequest(String url, Map<String, String> headers, String jsonBody) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
    for (Map.Entry<String, String> h : headers.entrySet()) {
      b.header(h.getKey(), h.getValue());
    }
    b.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
    return b.build();
  }
}
