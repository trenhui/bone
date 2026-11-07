package com.bone.aidoc.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HttpClientTransport 重试语义（真实 HttpServer 端到端）： 瞬时传输失败（读超时/连接中断）有限重试 + 退避抖动； 非 2xx
 * 与解析前置错误<b>绝不重试</b>（避免重试风暴）；耗尽后抛出，fail-closed 不变。
 */
class HttpClientTransportRetryTest {

  private HttpServer server;
  private ExecutorService executor;
  private String baseUrl;
  private AtomicInteger requests;
  private int failUntil; // 请求序号 <= failUntil 时模拟传输超时（应被重试）

  @BeforeEach
  void start() throws IOException {
    requests = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    // 默认 HttpServer 单线程分发，handler 内 sleep 会阻塞后续重试请求；
    // 用缓存线程池让每次请求独立成线，才能真实模拟「前几次慢/挂、后续成功」的瞬时故障重试。
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/ocr",
        exchange -> {
          int n = requests.incrementAndGet();
          if (n <= failUntil) {
            // 模拟瞬时传输失败：服务端处理超时导致客户端读超时触发 IOException，应被重试
            try {
              Thread.sleep(2000);
            } catch (InterruptedException ignored) {
              Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
          }
          byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  void transient_failure_is_retried_then_succeeds() throws Exception {
    failUntil = 2; // 前 2 次超时，第 3 次成功；maxRetries=2 → 共 3 次请求
    var transport = new HttpOcrClient.HttpClientTransport(200, 2, 1);
    String body = transport.post(baseUrl + "/ocr", "{}");
    assertThat(body).isEqualTo("{\"ok\":true}");
    assertThat(requests.get()).isEqualTo(3);
  }

  @Test
  void exhausted_transient_failures_still_throw() {
    failUntil = 99; // 始终超时；maxRetries=2 → 共 3 次后抛出
    var transport = new HttpOcrClient.HttpClientTransport(200, 2, 1);
    assertThatThrownBy(() -> transport.post(baseUrl + "/ocr", "{}"))
        .isInstanceOf(IOException.class);
    assertThat(requests.get()).isEqualTo(3);
  }

  @Test
  void non_2xx_is_not_retried() throws Exception {
    // 服务端始终返回 500（确定性错误），即便 maxRetries 很大也只调用 1 次
    server.stop(0);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/ocr",
        exchange -> {
          requests.incrementAndGet();
          byte[] body = "err".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ocr";
    var transport = new HttpOcrClient.HttpClientTransport(200, 5, 1);
    assertThatThrownBy(() -> transport.post(url, "{}")).isInstanceOf(IOException.class);
    assertThat(requests.get()).isEqualTo(1);
  }

  @Test
  void configured_max_retries_is_exposed() {
    var transport = new HttpOcrClient.HttpClientTransport(1000, 3, 20);
    assertThat(transport.maxRetries()).isEqualTo(3);
  }
}
