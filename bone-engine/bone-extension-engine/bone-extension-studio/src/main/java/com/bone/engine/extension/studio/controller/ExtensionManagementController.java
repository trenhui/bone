package com.bone.engine.extension.studio.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.model.ResultCode;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import com.bone.engine.extension.studio.service.ExtPointService;
import com.bone.engine.extension.studio.service.ExtensionService;
import com.bone.engine.extension.studio.service.PluginExecutionLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 扩展管理 REST API（契约：doc/design/modules/5. 扩展管理模块详细设计方案.md §5）。
 *
 * <p>路径前缀 {@code /api/v1/extension}（见 Bone-API-规范 §13.1）。
 */
@RestController
@RequestMapping("/api/v1/extension")
@RequiredArgsConstructor
public class ExtensionManagementController {

    private final ExtPointService extPointService;
    private final ExtensionService extensionService;
    private final PluginExecutionLogService executionLogService;
    private final ExtensionStudioProperties studioProperties;

    @GetMapping("/points")
    public ResponseEntity<ApiResponse<?>> listPoints(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<ExtPoint> points;
        if (hasText(keyword)) {
            points = extPointService.searchExtPoints(keyword);
        } else if (hasText(domain) && hasText(category)) {
            points = extPointService.findExtPointsByDomain(domain).stream()
                    .filter(p -> category.equals(p.getCategory()))
                    .toList();
        } else if (hasText(domain)) {
            points = extPointService.findExtPointsByDomain(domain);
        } else if (hasText(category)) {
            points = extPointService.findExtPointsByCategory(category);
        } else {
            points = extPointService.findAllExtPoints();
        }
        if (page != null || size != null) {
            return ResponseEntity.ok(
                    ApiResponse.success("获取扩展点列表成功", slicePage(points, page, size)));
        }
        return ResponseEntity.ok(ApiResponse.success("获取扩展点列表成功", points));
    }

    @GetMapping("/points/{id}")
    public ResponseEntity<ApiResponse<ExtPoint>> getPoint(@PathVariable Long id) {
        ExtPoint point = extPointService.findExtPointById(id);
        if (point == null) {
            return notFound("扩展点不存在");
        }
        return ResponseEntity.ok(ApiResponse.success("获取扩展点详情成功", point));
    }

    @PostMapping("/points")
    public ResponseEntity<ApiResponse<ExtPoint>> createPoint(@RequestBody ExtPoint body) {
        if (body == null || !hasText(body.getName()) || !hasText(body.getInterfaceName())) {
            return badRequest("扩展点名称与 interfaceName 不能为空");
        }
        ExtPoint saved = extPointService.saveExtPoint(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("创建扩展点成功", saved));
    }

    @PutMapping("/points/{id}")
    public ResponseEntity<ApiResponse<ExtPoint>> updatePoint(@PathVariable Long id, @RequestBody ExtPoint body) {
        ExtPoint updated = extPointService.updateExtPoint(id, body);
        if (updated == null) {
            return notFound("扩展点不存在");
        }
        return ResponseEntity.ok(ApiResponse.success("更新扩展点成功", updated));
    }

    @DeleteMapping("/points/{id}")
    public ResponseEntity<Void> deletePoint(@PathVariable Long id) {
        extPointService.deleteExtPoint(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/points/{id}:enable")
    public ResponseEntity<ApiResponse<ExtPoint>> enablePoint(@PathVariable Long id) {
        return togglePoint(id, true);
    }

    @PostMapping("/points/{id}:disable")
    public ResponseEntity<ApiResponse<ExtPoint>> disablePoint(@PathVariable Long id) {
        return togglePoint(id, false);
    }

    @GetMapping("/plugins")
    public ResponseEntity<ApiResponse<?>> listPlugins(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long extPointId,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<Extension> plugins;
        if (hasText(keyword)) {
            plugins = extensionService.searchExtensions(keyword);
        } else if (extPointId != null) {
            plugins = extensionService.findExtensionsByExtPointId(extPointId);
        } else if (hasText(tenantCode)) {
            plugins = extensionService.findExtensionsByTenantCode(tenantCode);
        } else {
            plugins = extensionService.findAllExtensions();
        }
        if (page != null || size != null) {
            return ResponseEntity.ok(
                    ApiResponse.success("获取插件列表成功", slicePage(plugins, page, size)));
        }
        return ResponseEntity.ok(ApiResponse.success("获取插件列表成功", plugins));
    }

    @GetMapping("/plugins/{id}")
    public ResponseEntity<ApiResponse<Extension>> getPlugin(@PathVariable Long id) {
        Extension extension = extensionService.findExtensionById(id);
        if (extension == null) {
            return notFound("插件不存在");
        }
        return ResponseEntity.ok(ApiResponse.success("获取插件详情成功", extension));
    }

    @GetMapping("/plugins/{id}/doc")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPluginDoc(@PathVariable Long id) {
        Extension extension = extensionService.findExtensionById(id);
        if (extension == null) {
            return notFound("插件不存在");
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", extension.getId());
        doc.put("name", extension.getName());
        doc.put("className", extension.getClassName());
        doc.put("tenantCode", extension.getTenantCode());
        doc.put("bizCode", extension.getBizCode());
        doc.put("priority", extension.getPriority());
        doc.put("enabled", extension.isEnabled());
        doc.put("description", extension.getDescription());
        doc.put("createdAt", extension.getCreatedAt());
        doc.put("updatedAt", extension.getUpdatedAt());
        return ResponseEntity.ok(ApiResponse.success("获取插件文档成功", doc));
    }

    @PostMapping("/plugins")
    public ResponseEntity<ApiResponse<Extension>> createPlugin(@RequestBody Extension body) {
        try {
            if (body == null || body.getExtPointId() == null || !hasText(body.getName()) || !hasText(body.getClassName())) {
                return badRequest("extPointId、name、className 不能为空");
            }
            Extension saved = extensionService.saveExtension(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("创建插件成功", saved));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PutMapping("/plugins/{id}")
    public ResponseEntity<ApiResponse<Extension>> updatePlugin(@PathVariable Long id, @RequestBody Extension body) {
        Extension updated = extensionService.updateExtension(id, body);
        if (updated == null) {
            return notFound("插件不存在");
        }
        return ResponseEntity.ok(ApiResponse.success("更新插件成功", updated));
    }

    @DeleteMapping("/plugins/{id}")
    public ResponseEntity<Void> deletePlugin(@PathVariable Long id) {
        extensionService.deleteExtension(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plugins/{id}/versions")
    public ResponseEntity<ApiResponse<List<PluginVersion>>> listPluginVersions(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    ApiResponse.success("获取插件版本列表成功", extensionService.listPluginVersions(id)));
        } catch (IllegalArgumentException ex) {
            return notFound(ex.getMessage());
        }
    }

    @PostMapping(value = "/plugins:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Extension>> uploadPlugin(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "extPointId", required = false) Long extPointId,
            @RequestParam("name") String name,
            @RequestParam(value = "version", defaultValue = "1.0.0") String version,
            @RequestParam("className") String className,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "pluginId", required = false) Long pluginId) {
        try {
            Extension saved = extensionService.uploadPluginArtifact(
                    file, extPointId, name, version, className, description, pluginId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("上传插件成功", saved));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PostMapping("/plugins/{id}:deploy")
    public ResponseEntity<ApiResponse<Extension>> deployPlugin(@PathVariable Long id) {
        return lifecycle(id, true, "部署");
    }

    @PostMapping("/plugins/{id}:undeploy")
    public ResponseEntity<ApiResponse<Extension>> undeployPlugin(@PathVariable Long id) {
        return lifecycle(id, false, "卸载");
    }

    @PostMapping("/plugins/{id}:rollback")
    public ResponseEntity<ApiResponse<Extension>> rollbackPlugin(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            String version = body != null && body.get("version") != null ? body.get("version").toString() : null;
            extensionService.rollbackExtension(id, version);
            Extension extension = extensionService.findExtensionById(id);
            return ResponseEntity.ok(ApiResponse.success("回滚插件成功", extension));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PostMapping("/plugins/{id}:publish-runtime")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishRuntime(@PathVariable Long id) {
        try {
            boolean published = extensionService.publishRuntime(id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("published", published);
            return ResponseEntity.ok(ApiResponse.success("发布运行时元数据成功", data));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PostMapping("/plugins/{id}:bind")
    public ResponseEntity<ApiResponse<Extension>> bindPlugin(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object extPointId = body != null ? body.get("extensionPointId") : null;
        if (extPointId == null) {
            extPointId = body != null ? body.get("extPointId") : null;
        }
        if (extPointId == null) {
            return badRequest("extensionPointId 不能为空");
        }
        Extension extension = extensionService.findExtensionById(id);
        if (extension == null) {
            return notFound("插件不存在");
        }
        extension.setExtPointId(Long.valueOf(extPointId.toString()));
        extensionService.updateExtension(id, extension);
        return ResponseEntity.ok(ApiResponse.success("绑定扩展点成功", extension));
    }

    @PostMapping("/plugins/{id}:unbind")
    public ResponseEntity<ApiResponse<Extension>> unbindPlugin(@PathVariable Long id) {
        Extension extension = extensionService.findExtensionById(id);
        if (extension == null) {
            return notFound("插件不存在");
        }
        if (extension.isEnabled()) {
            extensionService.undeployExtension(id);
        }
        return ResponseEntity.ok(ApiResponse.success("解除绑定成功", extension));
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview() {
        Map<String, Object> data = executionLogService.overview(
                extPointService.findAllExtPoints().size(),
                extensionService.findAllExtensions().size(),
                studioProperties.getRuntimeSync().isEnabled());
        return ResponseEntity.ok(ApiResponse.success("获取扩展概览成功", data));
    }

    @PostMapping({"/execution-logs/ingest", "/execution-logs:ingest"})
    public ResponseEntity<ApiResponse<PluginExecutionLog>> ingestExecutionLog(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("className") == null) {
            return badRequest("className 不能为空");
        }
        String className = body.get("className").toString();
        String methodName = body.get("methodName") != null ? body.get("methodName").toString() : null;
        String status = body.get("status") != null ? body.get("status").toString() : "SUCCESS";
        Long durationMs = null;
        if (body.get("durationMs") != null) {
            durationMs = Long.valueOf(body.get("durationMs").toString());
        }
        String errorMessage = body.get("errorMessage") != null ? body.get("errorMessage").toString() : null;
        return executionLogService
                .ingestFromRuntime(className, methodName, status, durationMs, errorMessage)
                .map(log -> ResponseEntity.ok(ApiResponse.success("执行日志已记录", log)))
                .orElseGet(() -> notFound("未找到 className 对应的插件: " + className));
    }

    @GetMapping("/execution-logs")
    public ResponseEntity<ApiResponse<PageResult<PluginExecutionLog>>> executionLogs(
            @RequestParam(required = false) Long pluginId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<PluginExecutionLog> list = executionLogService.query(pluginId, status, safePage, safeSize);
        long total = executionLogService.count(pluginId, status);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "获取执行日志成功", PageResult.of(list, total, safePage, safeSize)));
    }

    @PostMapping("/plugins/{id}:simulate")
    public ResponseEntity<ApiResponse<PluginExecutionLog>> simulatePlugin(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("模拟调用成功", extensionService.simulatePluginExecution(id)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @GetMapping("/sandbox/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sandboxConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("runtime", "WasmEdge (planned)");
        config.put("maxMemoryMb", 512);
        config.put("maxCpuCores", 0.5);
        config.put("timeoutSeconds", 30);
        config.put("syncEnabled", studioProperties.getRuntimeSync().isEnabled());
        config.putAll(executionLogService.overview(
                extPointService.findAllExtPoints().size(),
                extensionService.findAllExtensions().size(),
                studioProperties.getRuntimeSync().isEnabled()));
        return ResponseEntity.ok(ApiResponse.success("获取沙箱配置成功", config));
    }

    private ResponseEntity<ApiResponse<ExtPoint>> togglePoint(Long id, boolean enabled) {
        ExtPoint updated = extPointService.enableExtPoint(id, enabled);
        if (updated == null) {
            return notFound("扩展点不存在");
        }
        return ResponseEntity.ok(
                ApiResponse.success((enabled ? "启用" : "禁用") + "扩展点成功", updated));
    }

    private ResponseEntity<ApiResponse<Extension>> lifecycle(Long id, boolean deploy, String action) {
        try {
            if (deploy) {
                extensionService.deployExtension(id);
            } else {
                extensionService.undeployExtension(id);
            }
            Extension extension = extensionService.findExtensionById(id);
            return ResponseEntity.ok(ApiResponse.success(action + "插件成功", extension));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> PageResult<T> slicePage(List<T> all, Integer page, Integer size) {
        int safePage = page != null ? Math.max(1, page) : 1;
        int safeSize = size != null ? Math.min(100, Math.max(1, size)) : 20;
        int from = (safePage - 1) * safeSize;
        List<T> slice;
        if (from >= all.size()) {
            slice = List.of();
        } else {
            slice = all.subList(from, Math.min(from + safeSize, all.size()));
        }
        return PageResult.of(slice, (long) all.size(), safePage, safeSize);
    }

    private static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ResultCode.BAD_REQUEST.getCode(), message));
    }

    private static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ResultCode.NOT_FOUND.getCode(), message));
    }
}
