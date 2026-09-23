package com.bone.engine.extension.studio.application;

import com.bone.core.model.ApiResponse;
import com.bone.engine.extension.studio.application.support.PluginArtifactSupport;
import com.bone.engine.extension.studio.application.support.StudioAuditSupport;
import com.bone.engine.extension.studio.application.support.StudioCommandResponses;
import com.bone.engine.extension.studio.application.support.StudioIdempotencySupport;
import com.bone.engine.extension.studio.application.support.StudioIdempotentExecutor;
import com.bone.engine.extension.studio.application.support.StudioLroSupport;
import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import com.bone.engine.extension.studio.domain.model.execution.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.plugin.PluginVersion;
import com.bone.engine.extension.studio.domain.repository.PluginVersionRepository;
import com.bone.engine.extension.studio.observability.StudioExtensionMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 插件写侧编排：幂等、审计、LRO 与 {@link ExtensionCommandApplicationService} 组合。 */
@Component
@RequiredArgsConstructor
public class ExtensionStudioApplicationService {

  private static final String PATH_PLUGINS = "/api/v1/extension/plugins";
  private static final String PATH_UPLOAD = "/api/v1/extension/plugins:upload";

  private final ExtensionCommandApplicationService extensionCommandHandler;
  private final ExtensionQueryApplicationService extensionQueryHandler;
  private final PluginVersionRepository pluginVersionRepository;
  private final PluginArtifactSupport pluginArtifactService;
  private final StudioIdempotentExecutor idempotentExecutor;
  private final StudioAuditSupport auditService;
  private final StudioLroSupport lroService;
  private final ExtensionStudioProperties studioProperties;
  private final ObjectMapper objectMapper;
  private final StudioExtensionMetrics studioMetrics;

  public ResponseEntity<ApiResponse<Extension>> createPlugin(String idempotencyKey, Extension body)
      throws JsonProcessingException {
    return createPlugin(
        idempotencyKey,
        body,
        StudioIdempotencySupport.fingerprint(objectMapper.writeValueAsString(body)));
  }

  public ResponseEntity<ApiResponse<Extension>> createPlugin(
      String idempotencyKey, Extension body, String requestFingerprint) {
    if (body == null
        || body.getExtPointId() == null
        || !hasText(body.getName())
        || !hasText(body.getClassName())) {
      return StudioCommandResponses.badRequest("extPointId、name、className 不能为空");
    }
    try {
      return idempotentExecutor.execute(
          idempotencyKey,
          "POST",
          PATH_PLUGINS,
          requestFingerprint,
          () -> {
            Extension saved = extensionCommandHandler.saveExtension(body);
            auditService.success("plugin.create", "plugin", String.valueOf(saved.getId()));
            return StudioCommandResponses.created(
                StudioCommandResponses.resourceLocation("plugins", saved.getId()), "创建插件成功", saved);
          });
    } catch (IllegalArgumentException ex) {
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  public ResponseEntity<ApiResponse<Extension>> updatePlugin(
      Long id, Extension body, Integer expectedVersion) {
    Extension updated = extensionCommandHandler.updateExtension(id, body, expectedVersion);
    if (updated == null) {
      return StudioCommandResponses.notFound("插件不存在");
    }
    auditService.success("plugin.update", "plugin", String.valueOf(id));
    return StudioCommandResponses.ok("更新插件成功", updated);
  }

  public ResponseEntity<ApiResponse<Extension>> patchPlugin(
      Long id, Map<String, Object> body, Integer expectedVersion) {
    try {
      Extension updated = extensionCommandHandler.patchExtension(id, body, expectedVersion);
      if (updated == null) {
        return StudioCommandResponses.notFound("插件不存在");
      }
      auditService.success("plugin.patch", "plugin", String.valueOf(id));
      return StudioCommandResponses.ok("部分更新插件成功", updated);
    } catch (IllegalArgumentException ex) {
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  public ResponseEntity<Resource> downloadPluginVersion(Long pluginId, String version) {
    if (extensionQueryHandler.findExtensionById(pluginId) == null) {
      return ResponseEntity.notFound().build();
    }
    PluginVersion pv = pluginVersionRepository.findByPluginVersion(pluginId, version);
    if (pv == null) {
      return ResponseEntity.notFound().build();
    }
    try {
      Resource resource = pluginArtifactService.openArtifact(pv.getFilePath());
      String filename = pluginId + "-" + version.replaceAll("[^a-zA-Z0-9._-]", "_") + ".jar";
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
          .body(resource);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.notFound().build();
    } catch (IOException ex) {
      return ResponseEntity.internalServerError().build();
    }
  }

  public ResponseEntity<Void> deletePlugin(Long id) {
    extensionCommandHandler.deleteExtension(id);
    auditService.success("plugin.delete", "plugin", String.valueOf(id));
    return ResponseEntity.noContent().build();
  }

  public ResponseEntity<ApiResponse<Extension>> uploadPlugin(
      String idempotencyKey,
      MultipartFile file,
      Long extPointId,
      String name,
      String version,
      String className,
      String description,
      Long pluginId) {
    String fingerprint =
        StudioIdempotencySupport.fingerprint(
            name
                + "|"
                + version
                + "|"
                + className
                + "|"
                + extPointId
                + "|"
                + pluginId
                + "|"
                + file.getSize());
    return uploadPlugin(
        idempotencyKey,
        fingerprint,
        file,
        extPointId,
        name,
        version,
        className,
        description,
        pluginId);
  }

  public ResponseEntity<ApiResponse<Extension>> uploadPlugin(
      String idempotencyKey,
      String requestFingerprint,
      MultipartFile file,
      Long extPointId,
      String name,
      String version,
      String className,
      String description,
      Long pluginId) {
    try {
      return idempotentExecutor.execute(
          idempotencyKey,
          "POST",
          PATH_UPLOAD,
          requestFingerprint,
          () -> {
            Extension saved =
                extensionCommandHandler.uploadPluginArtifact(
                    file, extPointId, name, version, className, description, pluginId);
            auditService.success("plugin.upload", "plugin", String.valueOf(saved.getId()));
            return StudioCommandResponses.created(
                StudioCommandResponses.resourceLocation("plugins", saved.getId()), "上传插件成功", saved);
          });
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  public ResponseEntity<?> deployPlugin(Long id, Boolean syncParam, String idempotencyKey) {
    String path = "/api/v1/extension/plugins/" + id + ":deploy";
    String fingerprint = StudioIdempotencySupport.fingerprint("");
    if (resolveDeploySync(syncParam)) {
      return idempotentExecutor.execute(
          idempotencyKey, "POST", path, fingerprint, () -> lifecycle(id, true, "部署"));
    }
    return idempotentExecutor.execute(
        idempotencyKey,
        "POST",
        path,
        fingerprint,
        () -> {
          if (extensionQueryHandler.findExtensionById(id) == null) {
            return StudioCommandResponses.<Map<String, Object>>notFound("插件不存在");
          }
          String operationId = lroService.startPluginDeploy(id);
          Map<String, Object> accepted = new LinkedHashMap<>();
          accepted.put("operationId", operationId);
          return StudioCommandResponses.accepted(
              StudioCommandResponses.operationLocation(operationId), "部署任务已接受", accepted);
        });
  }

  public ResponseEntity<ApiResponse<Extension>> rollbackPlugin(
      Long id, String idempotencyKey, Map<String, Object> body) throws JsonProcessingException {
    String fingerprint =
        StudioIdempotencySupport.fingerprint(
            body != null ? objectMapper.writeValueAsString(body) : "");
    String version =
        body != null && body.get("version") != null ? body.get("version").toString() : null;
    return rollbackPlugin(id, idempotencyKey, fingerprint, version);
  }

  public ResponseEntity<ApiResponse<Extension>> rollbackPlugin(
      Long id, String idempotencyKey, String requestFingerprint, String version) {
    try {
      return idempotentExecutor.execute(
          idempotencyKey,
          "POST",
          "/api/v1/extension/plugins/" + id + ":rollback",
          requestFingerprint,
          () -> {
            extensionCommandHandler.rollbackExtension(id, version);
            Extension extension = extensionQueryHandler.findExtensionById(id);
            auditService.success("plugin.rollback", "plugin", String.valueOf(id));
            return StudioCommandResponses.ok("回滚插件成功", extension);
          });
    } catch (IllegalArgumentException ex) {
      auditService.failure("plugin.rollback", "plugin", String.valueOf(id), ex.getMessage());
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  public ResponseEntity<ApiResponse<Map<String, Object>>> publishRuntime(Long id) {
    try {
      boolean published = extensionCommandHandler.publishRuntime(id);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("id", id);
      data.put("published", published);
      auditService.success("plugin.publish_runtime", "plugin", String.valueOf(id));
      return ResponseEntity.ok(ApiResponse.success("发布运行时元数据成功", data));
    } catch (IllegalArgumentException | IllegalStateException ex) {
      auditService.failure("plugin.publish_runtime", "plugin", String.valueOf(id), ex.getMessage());
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  public ResponseEntity<ApiResponse<Extension>> undeployPlugin(Long id) {
    return lifecycle(id, false, "卸载");
  }

  public ResponseEntity<ApiResponse<Extension>> bindPlugin(Long id, Long extPointId) {
    Extension extension = extensionQueryHandler.findExtensionById(id);
    if (extension == null) {
      return StudioCommandResponses.notFound("插件不存在");
    }
    extension.setExtPointId(extPointId);
    extensionCommandHandler.updateExtension(id, extension);
    return ResponseEntity.ok(ApiResponse.success("绑定扩展点成功", extension));
  }

  public ResponseEntity<ApiResponse<Extension>> unbindPlugin(Long id) {
    Extension extension = extensionQueryHandler.findExtensionById(id);
    if (extension == null) {
      return StudioCommandResponses.notFound("插件不存在");
    }
    if (extension.isEnabled()) {
      extensionCommandHandler.undeployExtension(id);
    }
    return ResponseEntity.ok(ApiResponse.success("解除绑定成功", extension));
  }

  public ResponseEntity<ApiResponse<PluginExecutionLog>> simulatePlugin(Long id) {
    try {
      return ResponseEntity.ok(
          ApiResponse.success("模拟调用成功", extensionCommandHandler.simulatePluginExecution(id)));
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  private ResponseEntity<ApiResponse<Extension>> lifecycle(Long id, boolean deploy, String action) {
    String auditAction = deploy ? "plugin.deploy" : "plugin.undeploy";
    String metricAction = deploy ? "deploy" : "undeploy";
    try {
      if (deploy) {
        extensionCommandHandler.deployExtension(id);
      } else {
        extensionCommandHandler.undeployExtension(id);
      }
      Extension extension = extensionQueryHandler.findExtensionById(id);
      auditService.success(auditAction, "plugin", String.valueOf(id));
      studioMetrics.recordDeploy(metricAction, "success");
      return StudioCommandResponses.ok(action + "插件成功", extension);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      auditService.failure(auditAction, "plugin", String.valueOf(id), ex.getMessage());
      studioMetrics.recordDeploy(metricAction, "failure");
      return StudioCommandResponses.badRequest(ex.getMessage());
    }
  }

  private boolean resolveDeploySync(Boolean syncParam) {
    if (!studioProperties.getLro().isDeployEnabled()) {
      return true;
    }
    if (syncParam != null) {
      return syncParam;
    }
    return studioProperties.getLro().isDeploySyncByDefault();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
