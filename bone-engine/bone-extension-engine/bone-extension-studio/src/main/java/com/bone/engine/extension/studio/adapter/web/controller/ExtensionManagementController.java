package com.bone.engine.extension.studio.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.engine.extension.studio.application.command.handler.ExtPointCommandHandler;
import com.bone.engine.extension.studio.application.command.handler.ExtensionStudioCommandHandler;
import com.bone.engine.extension.studio.application.command.handler.MarketplaceInstallCommandHandler;
import com.bone.engine.extension.studio.application.command.handler.PluginExecutionLogCommandHandler;
import com.bone.engine.extension.studio.application.query.dto.DeploymentStateView;
import com.bone.engine.extension.studio.application.query.dto.PluginDependencyGraph;
import com.bone.engine.extension.studio.application.query.handler.DeploymentStateQueryHandler;
import com.bone.engine.extension.studio.application.query.handler.ExtPointQueryHandler;
import com.bone.engine.extension.studio.application.query.handler.ExtensionQueryHandler;
import com.bone.engine.extension.studio.application.query.handler.PluginDependencyGraphQueryHandler;
import com.bone.engine.extension.studio.application.query.handler.PluginExecutionLogQueryHandler;
import com.bone.engine.extension.studio.application.query.handler.StudioAuditQueryHandler;
import com.bone.engine.extension.studio.application.query.handler.StudioOperationQueryHandler;
import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import com.bone.engine.extension.studio.domain.gateway.MarketplaceCatalog;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.MarketplaceItem;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import com.bone.engine.extension.studio.domain.model.StudioAuditEntry;
import com.bone.engine.extension.studio.domain.model.StudioOperation;
import com.bone.engine.extension.studio.security.ExtensionScopes;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 扩展管理 REST API（契约：doc/design/modules/5. 扩展管理模块详细设计方案.md §5）。
 *
 * <p>路径前缀 {@code /api/v1/extension}（见 Bone-API-规范 §13.1）。
 */
@Tag(name = "Extension Studio", description = "扩展点 / 插件 / 沙箱 / 执行日志")
@RestController
@RequestMapping("/api/v1/extension")
@RequiredArgsConstructor
public class ExtensionManagementController {

  private final ExtPointQueryHandler extPointQueryHandler;
  private final ExtPointCommandHandler extPointCommandHandler;
  private final ExtensionQueryHandler extensionQueryHandler;
  private final ExtensionStudioCommandHandler extensionStudioCommandHandler;
  private final PluginExecutionLogQueryHandler pluginExecutionLogQueryHandler;
  private final PluginExecutionLogCommandHandler pluginExecutionLogCommandHandler;
  private final StudioOperationQueryHandler studioOperationQueryHandler;
  private final StudioAuditQueryHandler studioAuditQueryHandler;
  private final ExtensionStudioProperties studioProperties;
  private final DeploymentStateQueryHandler deploymentStateQueryHandler;
  private final PluginDependencyGraphQueryHandler pluginDependencyGraphQueryHandler;
  private final MarketplaceCatalog marketplaceCatalog;
  private final MarketplaceInstallCommandHandler marketplaceInstallCommandHandler;

  @GetMapping("/points")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<?>> listPoints(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String domain,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    List<ExtPoint> points;
    if (hasText(keyword)) {
      points = extPointQueryHandler.searchExtPoints(keyword);
    } else if (hasText(domain) && hasText(category)) {
      points =
          extPointQueryHandler.findExtPointsByDomain(domain).stream()
              .filter(p -> category.equals(p.getCategory()))
              .toList();
    } else if (hasText(domain)) {
      points = extPointQueryHandler.findExtPointsByDomain(domain);
    } else if (hasText(category)) {
      points = extPointQueryHandler.findExtPointsByCategory(category);
    } else {
      points = extPointQueryHandler.findAllExtPoints();
    }
    if (page != null || size != null) {
      return ResponseEntity.ok(ApiResponse.success("获取扩展点列表成功", slicePage(points, page, size)));
    }
    return ResponseEntity.ok(ApiResponse.success("获取扩展点列表成功", points));
  }

  @GetMapping("/points/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<ExtPoint>> getPoint(@PathVariable Long id) {
    ExtPoint point = extPointQueryHandler.findExtPointById(id);
    if (point == null) {
      return notFound("扩展点不存在");
    }
    return StudioHttpSupport.ok("获取扩展点详情成功", point);
  }

  @PostMapping("/points")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<ExtPoint>> createPoint(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody ExtPoint body)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return extPointCommandHandler.createPoint(idempotencyKey, body);
  }

  @PutMapping("/points/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<ExtPoint>> updatePoint(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody ExtPoint body) {
    return extPointCommandHandler.updatePoint(
        id, body, StudioHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
  }

  @PatchMapping("/points/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<ExtPoint>> patchPoint(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody Map<String, Object> body) {
    return extPointCommandHandler.patchPoint(
        id, body, StudioHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
  }

  @DeleteMapping("/points/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<Void> deletePoint(@PathVariable Long id) {
    return extPointCommandHandler.deletePoint(id);
  }

  @PostMapping("/points/{id}:enable")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<ExtPoint>> enablePoint(@PathVariable Long id) {
    return togglePoint(id, true);
  }

  @PostMapping("/points/{id}:disable")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<ExtPoint>> disablePoint(@PathVariable Long id) {
    return togglePoint(id, false);
  }

  @GetMapping("/plugins")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<?>> listPlugins(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long extPointId,
      @RequestParam(required = false) String tenantCode,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    List<Extension> plugins;
    if (hasText(keyword)) {
      plugins = extensionQueryHandler.searchExtensions(keyword);
    } else if (extPointId != null) {
      plugins = extensionQueryHandler.findExtensionsByExtPointId(extPointId);
    } else if (hasText(tenantCode)) {
      plugins = extensionQueryHandler.findExtensionsByTenantCode(tenantCode);
    } else {
      plugins = extensionQueryHandler.findAllExtensions();
    }
    if (page != null || size != null) {
      return ResponseEntity.ok(ApiResponse.success("获取插件列表成功", slicePage(plugins, page, size)));
    }
    return ResponseEntity.ok(ApiResponse.success("获取插件列表成功", plugins));
  }

  @GetMapping("/plugins/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<Extension>> getPlugin(@PathVariable Long id) {
    Extension extension = extensionQueryHandler.findExtensionById(id);
    if (extension == null) {
      return notFound("插件不存在");
    }
    return StudioHttpSupport.ok("获取插件详情成功", extension);
  }

  @GetMapping("/plugins/{id}/doc")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getPluginDoc(@PathVariable Long id) {
    Extension extension = extensionQueryHandler.findExtensionById(id);
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
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<Extension>> createPlugin(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody Extension body)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return extensionStudioCommandHandler.createPlugin(idempotencyKey, body);
  }

  @PutMapping("/plugins/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<Extension>> updatePlugin(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody Extension body) {
    return extensionStudioCommandHandler.updatePlugin(
        id, body, StudioHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
  }

  @PatchMapping("/plugins/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<Extension>> patchPlugin(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody Map<String, Object> body) {
    return extensionStudioCommandHandler.patchPlugin(
        id, body, StudioHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
  }

  @GetMapping("/plugins/{id}/versions/{ver}:download")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<Resource> downloadPluginVersion(
      @PathVariable Long id, @PathVariable("ver") String version) {
    return extensionStudioCommandHandler.downloadPluginVersion(id, version);
  }

  @DeleteMapping("/plugins/{id}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<Void> deletePlugin(@PathVariable Long id) {
    return extensionStudioCommandHandler.deletePlugin(id);
  }

  @GetMapping("/plugins/{id}/versions")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<List<PluginVersion>>> listPluginVersions(
      @PathVariable Long id) {
    try {
      return ResponseEntity.ok(
          ApiResponse.success("获取插件版本列表成功", extensionQueryHandler.listPluginVersions(id)));
    } catch (IllegalArgumentException ex) {
      return notFound(ex.getMessage());
    }
  }

  @PostMapping(value = "/plugins:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<Extension>> uploadPlugin(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "extPointId", required = false) Long extPointId,
      @RequestParam("name") String name,
      @RequestParam(value = "version", defaultValue = "1.0.0") String version,
      @RequestParam("className") String className,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "pluginId", required = false) Long pluginId) {
    return extensionStudioCommandHandler.uploadPlugin(
        idempotencyKey, file, extPointId, name, version, className, description, pluginId);
  }

  @PostMapping("/plugins/{id}:deploy")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<?> deployPlugin(
      @PathVariable Long id,
      @RequestParam(required = false) Boolean sync,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return extensionStudioCommandHandler.deployPlugin(id, sync, idempotencyKey);
  }

  @GetMapping("/operations/{operationId}")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<StudioOperation>> getOperation(
      @PathVariable String operationId) {
    StudioOperation operation = studioOperationQueryHandler.getOperation(operationId);
    if (operation == null) {
      return notFound("操作不存在");
    }
    return ResponseEntity.ok(ApiResponse.success("获取操作状态成功", operation));
  }

  @PostMapping("/plugins/{id}:undeploy")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<Extension>> undeployPlugin(@PathVariable Long id) {
    return extensionStudioCommandHandler.undeployPlugin(id);
  }

  @PostMapping("/plugins/{id}:rollback")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<Extension>> rollbackPlugin(
      @PathVariable Long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) Map<String, Object> body)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return extensionStudioCommandHandler.rollbackPlugin(id, idempotencyKey, body);
  }

  @PostMapping("/plugins/{id}:publish-runtime")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> publishRuntime(@PathVariable Long id) {
    return extensionStudioCommandHandler.publishRuntime(id);
  }

  @PostMapping("/plugins/{id}:bind")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<Extension>> bindPlugin(
      @PathVariable Long id, @RequestBody Map<String, Object> body) {
    Object extPointId = body != null ? body.get("extensionPointId") : null;
    if (extPointId == null) {
      extPointId = body != null ? body.get("extPointId") : null;
    }
    if (extPointId == null) {
      return badRequest("extensionPointId 不能为空");
    }
    return extensionStudioCommandHandler.bindPlugin(id, Long.valueOf(extPointId.toString()));
  }

  @PostMapping("/plugins/{id}:unbind")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<Extension>> unbindPlugin(@PathVariable Long id) {
    return extensionStudioCommandHandler.unbindPlugin(id);
  }

  @GetMapping("/overview")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> overview() {
    Map<String, Object> data =
        pluginExecutionLogQueryHandler.overview(
            extPointQueryHandler.findAllExtPoints().size(),
            extensionQueryHandler.findAllExtensions().size(),
            studioProperties.getRuntimeSync().isEnabled());
    return ResponseEntity.ok(ApiResponse.success("获取扩展概览成功", data));
  }

  @PostMapping({"/execution-logs/ingest", "/execution-logs:ingest"})
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<PluginExecutionLog>> ingestExecutionLog(
      @RequestBody Map<String, Object> body) {
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
    String errorMessage =
        body.get("errorMessage") != null ? body.get("errorMessage").toString() : null;
    return pluginExecutionLogCommandHandler
        .ingestFromRuntime(className, methodName, status, durationMs, errorMessage)
        .map(log -> ResponseEntity.ok(ApiResponse.success("执行日志已记录", log)))
        .orElseGet(() -> notFound("未找到 className 对应的插件: " + className));
  }

  @GetMapping("/execution-logs")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<PageResult<PluginExecutionLog>>> executionLogs(
      @RequestParam(required = false) Long pluginId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletResponse response) {
    if (page != null) {
      int safePage = Math.max(1, page);
      int safeSize = size != null ? Math.min(100, Math.max(1, size)) : 20;
      List<PluginExecutionLog> list =
          pluginExecutionLogQueryHandler.query(pluginId, status, safePage, safeSize);
      long total = pluginExecutionLogQueryHandler.count(pluginId, status);
      return ResponseEntity.ok(
          ApiResponse.success("获取执行日志成功", PageResult.of(list, total, safePage, safeSize)));
    }
    int safeLimit = limit != null ? Math.min(100, Math.max(1, limit)) : 20;
    PageResult<PluginExecutionLog> result =
        pluginExecutionLogQueryHandler.queryByCursor(pluginId, status, cursor, safeLimit);
    if (result.getNextCursor() != null) {
      String nextLink =
          "/api/v1/extension/execution-logs?cursor="
              + result.getNextCursor()
              + "&limit="
              + safeLimit;
      if (pluginId != null) {
        nextLink += "&pluginId=" + pluginId;
      }
      if (hasText(status)) {
        nextLink += "&status=" + status;
      }
      response.setHeader("Link", "<" + nextLink + ">; rel=\"next\"");
    }
    return ResponseEntity.ok(ApiResponse.success("获取执行日志成功", result));
  }

  @PostMapping("/plugins/{id}:simulate")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.PLUGINS_DEPLOY + "')")
  public ResponseEntity<ApiResponse<PluginExecutionLog>> simulatePlugin(@PathVariable Long id) {
    return extensionStudioCommandHandler.simulatePlugin(id);
  }

  @GetMapping("/audit-logs")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<PageResult<StudioAuditEntry>>> auditLogs(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit,
      HttpServletResponse response) {
    int safeLimit = Math.min(100, Math.max(1, limit));
    PageResult<StudioAuditEntry> result =
        studioAuditQueryHandler.queryByCursor(action, resourceType, cursor, safeLimit);
    if (result.getNextCursor() != null) {
      StringBuilder link =
          new StringBuilder("/api/v1/extension/audit-logs?cursor=")
              .append(result.getNextCursor())
              .append("&limit=")
              .append(safeLimit);
      if (hasText(action)) {
        link.append("&action=").append(action);
      }
      if (hasText(resourceType)) {
        link.append("&resourceType=").append(resourceType);
      }
      response.setHeader("Link", "<" + link + ">; rel=\"next\"");
    }
    return ResponseEntity.ok(ApiResponse.success("获取审计日志成功", result));
  }

  @GetMapping("/plugins/{id}/deployment-state")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<DeploymentStateView>> deploymentState(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(
          ApiResponse.success("获取部署状态成功", deploymentStateQueryHandler.load(id)));
    } catch (IllegalArgumentException ex) {
      return notFound(ex.getMessage());
    }
  }

  @GetMapping("/dependency-graph")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<PluginDependencyGraph>> dependencyGraph(
      @RequestParam(required = false) Long extPointId) {
    return ResponseEntity.ok(
        ApiResponse.success("获取依赖图成功", pluginDependencyGraphQueryHandler.load(extPointId)));
  }

  @GetMapping("/marketplace")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<List<MarketplaceItem>>> marketplaceList(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String category) {
    return ResponseEntity.ok(
        ApiResponse.success("获取插件市场列表成功", marketplaceCatalog.list(keyword, category)));
  }

  @PostMapping("/marketplace/{itemId}:install")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_WRITE + "')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> marketplaceInstall(
      @PathVariable String itemId, @RequestBody(required = false) Map<String, Object> body) {
    Long extPointId =
        body != null && body.get("extPointId") != null
            ? Long.valueOf(body.get("extPointId").toString())
            : null;
    return marketplaceInstallCommandHandler.install(itemId, extPointId);
  }

  @GetMapping("/sandbox/config")
  @PreAuthorize("@studioSecurity.hasScope('" + ExtensionScopes.POINTS_READ + "')")
  public ResponseEntity<ApiResponse<Map<String, Object>>> sandboxConfig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("runtime", "JVM / bone-extension-sdk (As-Is)");
    config.put("runtimeVision", "Wasm/WASI");
    config.put("maxMemoryMb", 512);
    config.put("maxCpuCores", 0.5);
    config.put("timeoutSeconds", 30);
    config.put("syncEnabled", studioProperties.getRuntimeSync().isEnabled());
    config.putAll(
        pluginExecutionLogQueryHandler.overview(
            extPointQueryHandler.findAllExtPoints().size(),
            extensionQueryHandler.findAllExtensions().size(),
            studioProperties.getRuntimeSync().isEnabled()));
    return ResponseEntity.ok(ApiResponse.success("获取沙箱配置成功", config));
  }

  private ResponseEntity<ApiResponse<ExtPoint>> togglePoint(Long id, boolean enabled) {
    return extPointCommandHandler.enablePoint(id, enabled);
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
    return StudioApiResponses.badRequest(message);
  }

  private static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
    return StudioApiResponses.notFound(message);
  }
}
