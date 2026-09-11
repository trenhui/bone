package com.bone.system.infrastructure.gateway;

import com.bone.system.domain.gateway.ServiceHealthGateway;
import com.bone.system.domain.model.console.ServiceStatus;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * 主动探活 {@link ServiceHealthGateway} 实现。
 *
 * <p>对平台各微服务做 HTTP（优先 {@code /actuator/health}）探活，失败时降级为 TCP 端口连通性探测， 从而给出真实 {@code
 * UP/DOWN/UNKNOWN} 状态与耗时；全部探测并行执行且严格容错，绝不抛异常导致概览整体失败。
 */
@Component
public class HttpProbeServiceHealthGateway implements ServiceHealthGateway, DisposableBean {

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800)).build();
  private final ExecutorService executor = Executors.newFixedThreadPool(SERVICES.size());

  /** 平台微服务清单（名称 / 服务码 / 主机 / 端口）。 */
  private record Svc(String name, String code, String host, int port) {}

  private static final List<Svc> SERVICES =
      List.of(
          new Svc("IAM", "bone-iam", "localhost", 8081),
          new Svc("元数据", "bone-metadata-server", "localhost", 9001),
          new Svc("主数据", "bone-masterdata", "localhost", 8084),
          new Svc("集成", "bone-integration", "localhost", 8085),
          new Svc("系统管理", "bone-system", "localhost", 8083),
          new Svc("扩展 Studio", "bone-extension-studio", "localhost", 8088),
          new Svc("代码生成", "studio-generator", "localhost", 8086),
          new Svc("API 网关", "bone-gateway", "localhost", 8888));

  @Override
  public List<ServiceStatus> listServiceStatuses() {
    List<CompletableFuture<ServiceStatus>> futures = new ArrayList<>();
    for (Svc s : SERVICES) {
      futures.add(CompletableFuture.supplyAsync(() -> probe(s), executor));
    }
    List<ServiceStatus> result = new ArrayList<>();
    for (CompletableFuture<ServiceStatus> f : futures) {
      try {
        result.add(f.get(1500, TimeUnit.MILLISECONDS));
      } catch (Exception e) {
        result.add(fallback());
      }
    }
    return result;
  }

  private ServiceStatus probe(Svc s) {
    long start = System.nanoTime();
    // 1) 优先 HTTP 探活 actuator/health（多数 Spring Boot 服务暴露）
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create("http://" + s.host + ":" + s.port + "/actuator/health"))
              .timeout(Duration.ofMillis(1000))
              .GET()
              .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
        String status = parseStatus(resp.body());
        long ms = (System.nanoTime() - start) / 1_000_000;
        return of(s, status.isBlank() ? "UP" : status, ms);
      }
    } catch (Exception ignored) {
      // 降级到 TCP 探活
    }
    // 2) 兜底 TCP 端口连通性探测
    try (Socket sock = new Socket()) {
      sock.connect(new java.net.InetSocketAddress(s.host, s.port), 800);
      long ms = (System.nanoTime() - start) / 1_000_000;
      return of(s, "UP", ms);
    } catch (java.net.ConnectException ce) {
      long ms = (System.nanoTime() - start) / 1_000_000;
      return of(s, "DOWN", ms);
    } catch (Exception ignored) {
      // 超时或其它网络异常
    }
    return of(s, "UNKNOWN", 0);
  }

  private static ServiceStatus of(Svc s, String status, long ms) {
    return ServiceStatus.builder()
        .name(s.name)
        .serviceCode(s.code)
        .port(String.valueOf(s.port))
        .status(status)
        .latencyMs(ms)
        .build();
  }

  private static ServiceStatus fallback() {
    return ServiceStatus.builder()
        .name("未知服务")
        .serviceCode("unknown")
        .port("?")
        .status("UNKNOWN")
        .latencyMs(0)
        .build();
  }

  private static String parseStatus(String body) {
    if (body == null) return "";
    int i = body.indexOf("\"status\"");
    if (i < 0) return "";
    int c = body.indexOf('"', i + 8);
    if (c < 0) return "";
    int d = body.indexOf('"', c + 1);
    if (d < 0) return "";
    return body.substring(c + 1, d);
  }

  @Override
  public void destroy() {
    executor.shutdownNow();
  }
}
