package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.system.application.port.out.MetricValuePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统管理", description = "系统状态和健康检查接口")
@RestController
@RequestMapping(PlatformApiPaths.SYSTEM_V1)
@RequiredArgsConstructor
public class SystemController {
  private final HealthEndpoint healthEndpoint;
  private final MeterRegistry meterRegistry;
  private final MetricValuePort metricValuePort;

  @Operation(summary = "获取系统健康状态")
  @GetMapping("/health")
  public ApiResponse<Object> health() {
    return ApiResponse.success(healthEndpoint.health());
  }

  @Operation(summary = "获取系统信息")
  @GetMapping("/info")
  public ApiResponse<Map<String, Object>> info() {
    Map<String, Object> info = new HashMap<>();
    info.put("name", "bone-system");
    info.put("description", "Bone平台系统管理服务");
    info.put("version", "1.0.0");
    return ApiResponse.success(info);
  }

  @Operation(summary = "获取系统指标")
  @GetMapping("/metrics")
  public ApiResponse<Map<String, Object>> metrics() {
    Map<String, Object> metrics = new HashMap<>();
    // 原始 JVM 键（保留既有契约）
    metrics.put("jvm.memory.used", getGaugeValue("jvm.memory.used", 0.0));
    metrics.put("jvm.memory.max", getGaugeValue("jvm.memory.max", 0.0));
    metrics.put("jvm.threads.live", getGaugeValue("jvm.threads.live", 0.0));
    metrics.put("jvm.threads.daemon", getGaugeValue("jvm.threads.daemon", 0.0));
    // 前端监控卡片口径（MonitorAlert 归一化消费）：cpu/errorRate=百分数，memory.usage=堆占用百分数
    metrics.put("cpu", resolve("cpu.usage"));
    metrics.put("memory.usage", resolve("memory.usage"));
    metrics.put("disk", diskUsagePercent());
    metrics.put("errorRate", resolve("api.error_rate"));
    metrics.put("apiResponseTime", httpMeanMillis());
    metrics.put("qps", httpQps());
    metrics.put("dbConnections", resolve("db.connections.active"));
    return ApiResponse.success(metrics);
  }

  /** 经应用层指标端口取值（与告警评估同一口径）；端口异常/缺失时按 0 处理。 */
  private double resolve(String metricName) {
    OptionalDouble value = metricValuePort.resolve(metricName);
    return value == null ? 0.0 : value.orElse(0.0);
  }

  /** 本进程所在盘使用率（容器/K8s 场景即根文件系统口径）。 */
  private double diskUsagePercent() {
    try {
      java.io.File root = new java.io.File(".");
      long total = root.getTotalSpace();
      long usable = root.getUsableSpace();
      return total > 0 ? Math.round((total - usable) * 1000.0 / total) / 10.0 : 0.0;
    } catch (RuntimeException ex) {
      return 0.0;
    }
  }

  /** HTTP 平均响应时间（ms）：http.server.requests 全 timer 汇总均值，无样本时为 0。 */
  private double httpMeanMillis() {
    try {
      long count = 0;
      double totalMillis = 0;
      for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
        count += timer.count();
        totalMillis += timer.totalTime(TimeUnit.MILLISECONDS);
      }
      return count == 0 ? 0.0 : Math.round(totalMillis / count * 10) / 10.0;
    } catch (RuntimeException ex) {
      return 0.0;
    }
  }

  /** 近似 QPS：http.server.requests 累计计数 / 进程运行秒数（Micrometer 无内置窗口速率时的降级口径）。 */
  private double httpQps() {
    try {
      long count = 0;
      for (Timer timer : meterRegistry.find("http.server.requests").timers()) {
        count += timer.count();
      }
      io.micrometer.core.instrument.Gauge uptime = meterRegistry.find("process.uptime").gauge();
      double seconds = uptime == null ? 0.0 : uptime.value();
      return seconds > 0 ? Math.round(count / seconds * 100) / 100.0 : 0.0;
    } catch (RuntimeException ex) {
      return 0.0;
    }
  }

  private double getGaugeValue(String name, double defaultValue) {
    try {
      return meterRegistry.get(name).gauge().value();
    } catch (Exception e) {
      return defaultValue;
    }
  }

  // ---- 运维类接口（MVP 范围外，返回 501 而非 404，避免前端契约误报）----
  // 平台自身的部署/升级/重启/关停属于运维动作，MVP 不交付；实现它们会直接操作运行中的实例，
  // 在联调/生产环境都有误关停风险，因此显式声明「未实现」而非静默缺失。

  @Operation(summary = "部署新版本（运维动作，MVP 未实现）")
  @PostMapping("/deploy")
  public ResponseEntity<Void> deploy() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Operation(summary = "升级版本（运维动作，MVP 未实现）")
  @PostMapping("/upgrade")
  public ResponseEntity<Void> upgrade() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Operation(summary = "重启服务（运维动作，MVP 未实现）")
  @PostMapping("/restart")
  public ResponseEntity<Void> restart() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @Operation(summary = "关停服务（运维动作，MVP 未实现）")
  @PostMapping("/shutdown")
  public ResponseEntity<Void> shutdown() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}
