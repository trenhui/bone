package com.bone.iam.adapter.web.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扩展管理 API 联调占位（仅当 Studio 未部署且显式开启 stub 时生效）。
 * 生产/联调请启动 bone-extension-studio（8088），由网关或 Shell 将 {@code /api/extension} 转发至 Studio。
 */
@RestController
@RequestMapping("/api/extension")
@ConditionalOnProperty(prefix = "bone.iam.extension", name = "stub-enabled", havingValue = "true")
public class ExtensionE2eController {

    @GetMapping("/points")
    public Map<String, Object> listPoints() {
        return ok(
                "获取扩展点列表成功",
                List.of(
                        point(1L, "订单价格计算", "order", "pricing", true),
                        point(2L, "用户注册校验", "user", "validation", true)));
    }

    @PostMapping("/points/{id}/enable")
    public Map<String, Object> enablePoint(@PathVariable Long id) {
        return ok("启用扩展点成功", point(id, "扩展点-" + id, "demo", "default", true));
    }

    @PostMapping("/points/{id}/disable")
    public Map<String, Object> disablePoint(@PathVariable Long id) {
        return ok("禁用扩展点成功", point(id, "扩展点-" + id, "demo", "default", false));
    }

    @GetMapping("/plugins")
    public Map<String, Object> listPlugins() {
        return ok(
                "获取插件列表成功",
                List.of(
                        plugin(101L, 1L, "默认价格扩展", true),
                        plugin(102L, 1L, "VIP 价格扩展", false)));
    }

    @PostMapping("/plugins/{id}/deploy")
    public Map<String, Object> deploy(@PathVariable Long id) {
        return ok("部署插件成功", plugin(id, 1L, "插件-" + id, true));
    }

    @PostMapping("/plugins/{id}/undeploy")
    public Map<String, Object> undeploy(@PathVariable Long id) {
        return ok("卸载插件成功", plugin(id, 1L, "插件-" + id, false));
    }

    private static Map<String, Object> ok(String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    private static Map<String, Object> point(
            Long id, String name, String domain, String category, boolean enabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("domain", domain);
        m.put("category", category);
        m.put("enabled", enabled);
        m.put("interfaceName", "com.bone.demo.ExtPoint");
        m.put("description", "E2E 占位数据");
        return m;
    }

    private static Map<String, Object> plugin(Long id, Long extPointId, String name, boolean enabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("extPointId", extPointId);
        m.put("name", name);
        m.put("enabled", enabled);
        m.put("className", "com.bone.demo.Extension");
        m.put("description", "E2E 占位数据");
        return m;
    }
}
