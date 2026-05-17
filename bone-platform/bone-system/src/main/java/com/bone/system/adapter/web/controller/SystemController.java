package com.bone.system.adapter.web.controller;

import com.bone.core.web.PlatformApiPaths;
import com.bone.system.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
}
