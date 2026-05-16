package com.bone.system.adapter.web.controller;

import com.bone.system.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台与仪表盘聚合接口（对齐 {@code doc/design/modules/1. 控制台与仪表盘模块详细设计方案.md} §5）。
 * 当前由 bone-system 承载；后续可拆分为独立 console 服务。
 */
@Tag(name = "控制台", description = "系统概览、服务状态、资源与快捷操作")
@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class ConsoleController {

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;

    @Operation(summary = "获取系统概览（聚合服务、资源、关键指标）")
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("services", serviceStatuses());
        body.put("resourceUsage", resourceUsage());
        body.put("keyMetrics", keyMetrics());
        body.put("alerts", List.of());
        body.put("updatedAt", Instant.now().toString());
        return ApiResponse.success(body);
    }

    @Operation(summary = "获取各微服务状态摘要")
    @GetMapping("/services")
    public ApiResponse<List<Map<String, Object>>> services() {
        return ApiResponse.success(serviceStatuses());
    }

    @Operation(summary = "获取资源使用情况（当前节点 JVM 指标）")
    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> resources() {
        return ApiResponse.success(resourceUsage());
    }

    @Operation(summary = "获取关键业务指标占位与 JVM 指标")
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        return ApiResponse.success(keyMetrics());
    }

    @Operation(summary = "获取快速操作列表（与主应用导航对齐）")
    @GetMapping("/quick-actions")
    public ApiResponse<List<Map<String, String>>> quickActions() {
        List<Map<String, String>> actions = new ArrayList<>();
        actions.add(action("iam", "账号权限管理", "/iam", "UserOutlined"));
        actions.add(action("metadata", "元数据管理", "/metadata", "DatabaseOutlined"));
        actions.add(action("masterdata", "主数据管理", "/masterdata", "DatabaseOutlined"));
        actions.add(action("integration", "集成管理", "/integration", "LinkOutlined"));
        actions.add(action("system", "系统管理", "/system", "SettingOutlined"));
        actions.add(action("extension", "扩展管理", "/extension", "AppstoreOutlined"));
        return ApiResponse.success(actions);
    }

    private static Map<String, String> action(String id, String title, String path, String icon) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("path", path);
        m.put("icon", icon);
        return m;
    }

    private List<Map<String, Object>> serviceStatuses() {
        String localStatus = healthEndpoint.health().getStatus().getCode();
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(svc("IAM", "bone-iam", "8081", localStatus));
        list.add(svc("元数据", "bone-metadata-server", "—", "unknown"));
        list.add(svc("主数据", "bone-masterdata", "—", "unknown"));
        list.add(svc("集成", "bone-integration", "—", "unknown"));
        list.add(svc("系统管理", "bone-system", "8083", localStatus));
        list.add(svc("扩展 Studio", "bone-extension-studio", "8080", "unknown"));
        return list;
    }

    private static Map<String, Object> svc(String displayName, String code, String port, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", displayName);
        m.put("serviceCode", code);
        m.put("port", port);
        m.put("status", status);
        m.put("latencyMs", 0);
        return m;
    }

    private Map<String, Object> resourceUsage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cpuPercent", 0);
        m.put("memoryUsedBytes", (long) getGauge("jvm.memory.used", 0));
        m.put("memoryMaxBytes", (long) getGauge("jvm.memory.max", 0));
        m.put("diskUsedPercent", 0);
        m.put("updatedAt", Instant.now().toString());
        return m;
    }

    private Map<String, Object> keyMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCount", 0);
        m.put("userCount", 0);
        m.put("transactionAmount", 0);
        m.put("jvmThreadsLive", (long) getGauge("jvm.threads.live", 0));
        m.put("jvmThreadsDaemon", (long) getGauge("jvm.threads.daemon", 0));
        m.put("updatedAt", Instant.now().toString());
        return m;
    }

    private double getGauge(String name, double defaultValue) {
        try {
            return meterRegistry.get(name).gauge().value();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
