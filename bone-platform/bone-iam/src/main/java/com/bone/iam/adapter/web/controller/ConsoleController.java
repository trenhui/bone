package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制台聚合 API（联调期由 bone-iam 承载，与 bone-system 路径一致，便于 Shell 代理）。
 * 对齐 doc/design/modules/1. 控制台与仪表盘模块详细设计方案.md §5。
 */
@RestController
@RequestMapping(PlatformApiPaths.CONSOLE_V1)
public class ConsoleController {

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

    @GetMapping("/services")
    public ApiResponse<List<Map<String, Object>>> services() {
        return ApiResponse.success(serviceStatuses());
    }

    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> resources() {
        return ApiResponse.success(resourceUsage());
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        return ApiResponse.success(keyMetrics());
    }

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
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(svc("IAM", "bone-iam", "8081", "UP"));
        list.add(svc("元数据", "bone-metadata-server", "—", "unknown"));
        list.add(svc("主数据", "bone-masterdata", "—", "unknown"));
        list.add(svc("集成", "bone-integration", "—", "unknown"));
        list.add(svc("系统管理", "bone-system", "8083", "unknown"));
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
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long used = memory.getHeapMemoryUsage().getUsed();
        long max = memory.getHeapMemoryUsage().getMax();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cpuPercent", 0);
        m.put("memoryUsedBytes", used);
        m.put("memoryMaxBytes", max > 0 ? max : used);
        m.put("diskUsedPercent", 0);
        m.put("updatedAt", Instant.now().toString());
        return m;
    }

    private Map<String, Object> keyMetrics() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCount", 0);
        m.put("userCount", 0);
        m.put("transactionAmount", 0);
        m.put("jvmThreadsLive", threads.getThreadCount());
        m.put("jvmThreadsDaemon", threads.getDaemonThreadCount());
        m.put("updatedAt", Instant.now().toString());
        return m;
    }
}
