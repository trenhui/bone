package com.bone.system.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
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
    metrics.put("jvm.memory.used", getGaugeValue("jvm.memory.used", 0.0));
    metrics.put("jvm.memory.max", getGaugeValue("jvm.memory.max", 0.0));
    metrics.put("jvm.threads.live", getGaugeValue("jvm.threads.live", 0.0));
    metrics.put("jvm.threads.daemon", getGaugeValue("jvm.threads.daemon", 0.0));
    return ApiResponse.success(metrics);
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
