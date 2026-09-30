package com.bone.engine.extension.studio.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.engine.extension.studio.application.query.dto.SimulateResult;
import com.bone.engine.extension.studio.application.support.ExtensionValidationSupport;
import com.bone.engine.extension.studio.application.support.PluginArtifactSupport;
import com.bone.engine.extension.studio.application.support.PluginArtifactSupport.StoredArtifact;
import com.bone.engine.extension.studio.application.support.StudioPatchSupport;
import com.bone.engine.extension.studio.application.support.StudioVersionSupport;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.common.StudioErrors;
import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import com.bone.engine.extension.studio.domain.gateway.PluginVersionReadPort;
import com.bone.engine.extension.studio.domain.gateway.TenantDirectoryPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.model.plugin.DeploymentStatus;
import com.bone.engine.extension.studio.domain.model.plugin.PluginVersion;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.domain.repository.PluginVersionRepository;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 扩展写侧（含 Extension / PluginVersion 的创建、删除、回滚、上传）。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务写入 Extension / PluginVersion 聚合，当前不发布领域事件； 若将来接入事件发布，须改为调用
 * publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
public class ExtensionCommandApplicationService {

  private static final Logger log =
      LoggerFactory.getLogger(ExtensionCommandApplicationService.class);

  private final ExtensionRepository extensionRepository;
  private final ExtPointRepository extPointRepository;
  private final PluginVersionRepository pluginVersionRepository;
  private final PluginVersionReadPort pluginVersionReadPort;
  private final PluginArtifactSupport pluginArtifactService;
  private final PluginExecutionLogCommandApplicationService executionLogCommandHandler;
  private final ExtensionStudioProperties studioProperties;

  @Autowired(required = false)
  private RuntimeExtensionSyncService runtimeSyncService;

  /** metadata 模式下注入（只读 iam_tenant）；in-memory 联调无目录，校验降级为格式检查 */
  @Autowired(required = false)
  private TenantDirectoryPort tenantDirectoryPort;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional
  public Extension saveExtension(Extension extension) {
    if (extension.getExtPointId() != null
        && extPointRepository.findById(extension.getExtPointId()) == null) {
      throw StudioErrors.of(StudioErrorCodes.EXT_POINT_NOT_FOUND, extension.getExtPointId());
    }
    if (!StringUtils.hasText(extension.getTenantCode())) {
      extension.normalizeTenantCode();
    }
    ExtensionValidationSupport.assertTenantCodeValid(
        extension.getTenantCode(), tenantDirectoryPort);
    ExtensionValidationSupport.assertAppIdValid(extension.getAppId());
    if (extension.getVersion() == null) {
      extension.setVersion(1);
    }
    return extensionRepository.save(extension);
  }

  @Transactional
  public Extension updateExtension(Long id, Extension extension) {
    return updateExtension(id, extension, null);
  }

  @Transactional
  public Extension patchExtension(Long id, Map<String, Object> patch, Integer expectedVersion) {
    Extension existing = extensionRepository.findById(id);
    if (existing == null) {
      return null;
    }
    StudioVersionSupport.assertExpected(expectedVersion, existing.getVersion());
    StudioPatchSupport.applyToExtension(existing, patch);
    if (existing.getExtPointId() != null
        && extPointRepository.findById(existing.getExtPointId()) == null) {
      throw StudioErrors.of(StudioErrorCodes.EXT_POINT_NOT_FOUND, existing.getExtPointId());
    }
    ExtensionValidationSupport.assertTenantCodeValid(existing.getTenantCode(), tenantDirectoryPort);
    ExtensionValidationSupport.assertAppIdValid(existing.getAppId());
    existing.setVersion(StudioVersionSupport.nextVersion(existing.getVersion()));
    extensionRepository.save(existing);
    syncRuntimeMetadata(existing);
    return existing;
  }

  @Transactional
  public Extension updateExtension(Long id, Extension extension, Integer expectedVersion) {
    Extension existing = extensionRepository.findById(id);
    if (existing == null) {
      return null;
    }
    com.bone.engine.extension.studio.application.support.StudioVersionSupport.assertExpected(
        expectedVersion, existing.getVersion());
    if (extension.getExtPointId() != null) {
      existing.setExtPointId(extension.getExtPointId());
    }
    if (extension.getAppId() != null) {
      existing.assignApp(extension.getAppId());
    }
    ExtensionValidationSupport.assertTenantCodeValid(
        extension.getTenantCode(), tenantDirectoryPort);
    ExtensionValidationSupport.assertAppIdValid(existing.getAppId());
    existing.setName(extension.getName());
    existing.setDescription(extension.getDescription());
    existing.setClassName(extension.getClassName());
    existing.setTenantCode(extension.getTenantCode());
    existing.setBizCode(extension.getBizCode());
    if (extension.getUseCase() != null) {
      existing.setUseCase(extension.getUseCase());
    }
    if (extension.getScenario() != null) {
      existing.setScenario(extension.getScenario());
    }
    if (extension.getUserGroup() != null) {
      existing.setUserGroup(extension.getUserGroup());
    }
    existing.setPriority(extension.getPriority());
    existing.setConfig(extension.getConfig());
    existing.setEnabled(extension.isEnabled());
    existing.setVersion(
        com.bone.engine.extension.studio.application.support.StudioVersionSupport.nextVersion(
            existing.getVersion()));
    extensionRepository.save(existing);
    syncRuntimeMetadata(existing);
    return existing;
  }

  @Transactional
  public void deleteExtension(Long id) {
    Extension extension = extensionRepository.findById(id);
    if (extension != null && runtimeSyncService != null) {
      ExtPoint point = extPointRepository.findById(extension.getExtPointId());
      runtimeSyncService.unpublish(extension, point);
    }
    for (PluginVersion v : pluginVersionReadPort.findByPluginId(id)) {
      deleteArtifactFile(v.getFilePath());
      pluginVersionRepository.remove(v.getId());
    }
    extensionRepository.remove(id);
  }

  @Transactional
  public Extension enableExtension(Long id, boolean enabled) {
    Extension extension = extensionRepository.findById(id);
    if (extension == null) {
      return null;
    }
    extension.setEnabled(enabled);
    extensionRepository.save(extension);
    syncRuntimeMetadata(extension);
    return extension;
  }

  /**
   * 插件元数据变更后的运行时同步：仅对已发布到运行时存储的插件生效（probe 命中）。
   *
   * <p>业界控制面语义（对标配置中心/灰度发布平台）：运营在控制台修改启用开关、灰度流量、路由条件后 数据面必须即时感知；否则禁用形同虚设（Redis 里 enabled=true
   * 元数据残留）。启用 → 重新发布， 禁用 → 下架路由（数据面回退本地定义）。未发布过的插件不受影响。
   */
  private void syncRuntimeMetadata(Extension extension) {
    if (runtimeSyncService == null) {
      return;
    }
    // 同步前提（满足其一）：① 本插件有 ACTIVE 部署记录；② 本插件所属扩展点已被控制面接管
    // （种子型插件没有版本记录，只能以扩展点粒度判断接管状态）。
    boolean deployed =
        pluginVersionReadPort.findByPluginId(extension.getId()).stream()
            .anyMatch(v -> "ACTIVE".equals(String.valueOf(v.getDeploymentStatus())));
    boolean managed = deployed || runtimeSyncService.isExtensionPointManaged(extension);
    if (!managed) {
      return;
    }
    // 禁用不等于下架：禁用保留 enabled=false 的路由元数据，权威覆盖本地出厂定义
    // （否则数据面回退本地定义，禁用形同虚设）；真正移除路由（回退出厂行为）是 undeploy 的职责。
    runtimeSyncService.publish(extension);
  }

  @Transactional
  public Extension updateExtensionPriority(Long id, int priority) {
    Extension extension = extensionRepository.findById(id);
    if (extension == null) {
      return null;
    }
    extension.setPriority(priority);
    extensionRepository.save(extension);
    return extension;
  }

  @Transactional
  public void resetExtensionStatistics(Long id) {
    log.debug("resetExtensionStatistics id={}", id);
  }

  @Transactional
  public boolean deployExtension(Long id) {
    long start = System.currentTimeMillis();
    Extension extension = requireExtension(id);
    try {
      extension.enable();
      extensionRepository.save(extension);
      // 5a G3 生命周期分权：deployPublishes=false 时部署停在 STAGED，生效由 :publish-runtime 完成
      if (!studioProperties.getLifecycle().isDeployPublishes()) {
        markActiveVersionDeployment(id, DeploymentStatus.STAGED);
        logSuccess(
            extension, "DEPLOY", System.currentTimeMillis() - start, "{\"deployed\":\"staged\"}");
        return true;
      }
      markActiveVersionDeployment(id, DeploymentStatus.ACTIVE);
      boolean published = publishRuntime(id);
      logSuccess(
          extension,
          "DEPLOY",
          System.currentTimeMillis() - start,
          "{\"published\":" + published + "}");
      return published;
    } catch (RuntimeException ex) {
      logFailure(extension, "DEPLOY", System.currentTimeMillis() - start, ex.getMessage());
      throw ex;
    }
  }

  @Transactional
  public boolean undeployExtension(Long id) {
    long start = System.currentTimeMillis();
    Extension extension = requireExtension(id);
    try {
      if (runtimeSyncService != null) {
        ExtPoint point = extPointRepository.findById(extension.getExtPointId());
        runtimeSyncService.unpublish(extension, point);
      }
      extension.disable();
      extensionRepository.save(extension);
      markActiveVersionDeployment(id, DeploymentStatus.STAGED);
      logSuccess(extension, "UNDEPLOY", System.currentTimeMillis() - start, null);
      return true;
    } catch (RuntimeException ex) {
      logFailure(extension, "UNDEPLOY", System.currentTimeMillis() - start, ex.getMessage());
      throw ex;
    }
  }

  @Transactional
  public boolean publishRuntime(Long id) {
    long start = System.currentTimeMillis();
    Extension extension = requireExtension(id);
    try {
      if (!extension.isEnabled()) {
        throw StudioErrors.of(StudioErrorCodes.DEPLOY_STATE_INVALID, "插件未启用，无法发布到运行时");
      }
      if (runtimeSyncService == null) {
        log.warn("RuntimeExtensionSyncService 未配置，跳过运行时发布");
        markActiveVersionDeployment(id, DeploymentStatus.ACTIVE);
        logSuccess(
            extension, "PUBLISH_RUNTIME", System.currentTimeMillis() - start, "{\"skipped\":true}");
        return true;
      }
      List<String> errors = runtimeSyncService.validateForRuntime(extension);
      if (!errors.isEmpty()) {
        throw new IllegalArgumentException(String.join("; ", errors));
      }
      boolean ok = runtimeSyncService.publish(extension);
      markActiveVersionDeployment(id, DeploymentStatus.ACTIVE);
      logSuccess(
          extension,
          "PUBLISH_RUNTIME",
          System.currentTimeMillis() - start,
          "{\"published\":" + ok + "}");
      return ok;
    } catch (RuntimeException ex) {
      logFailure(extension, "PUBLISH_RUNTIME", System.currentTimeMillis() - start, ex.getMessage());
      throw ex;
    }
  }

  @Transactional
  public boolean rollbackExtension(Long id) {
    return rollbackExtension(id, null);
  }

  @Transactional
  public boolean rollbackExtension(Long id, String version) {
    Extension extension = requireExtension(id);
    List<PluginVersion> versions = pluginVersionReadPort.findByPluginId(id);
    if (versions.isEmpty()) {
      throw StudioErrors.of(StudioErrorCodes.PLUGIN_VERSION_NOT_FOUND, "插件 " + id + " 无可用版本");
    }

    PluginVersion target;
    if (StringUtils.hasText(version)) {
      target = pluginVersionRepository.findByPluginVersion(id, version.trim());
      if (target == null) {
        throw StudioErrors.of(StudioErrorCodes.PLUGIN_VERSION_NOT_FOUND, version);
      }
    } else {
      PluginVersion active = pluginVersionReadPort.findActiveByPluginId(id);
      target =
          versions.stream()
              .filter(v -> active == null || !v.getId().equals(active.getId()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("没有可回滚的上一个版本"));
    }

    boolean wasEnabled = extension.isEnabled();
    if (wasEnabled && runtimeSyncService != null) {
      ExtPoint point = extPointRepository.findById(extension.getExtPointId());
      runtimeSyncService.unpublish(extension, point);
    }

    for (PluginVersion v : versions) {
      boolean isTarget = v.getId().equals(target.getId());
      v.setActive(isTarget);
      v.setDeploymentStatus(
          isTarget
              ? (wasEnabled ? DeploymentStatus.ACTIVE : DeploymentStatus.STAGED)
              : DeploymentStatus.DEPRECATED);
      pluginVersionRepository.save(v);
    }

    extension.setConfig(mergeArtifactConfig(extension.getConfig(), target));
    extensionRepository.save(extension);

    if (wasEnabled) {
      publishRuntime(id);
    }
    log.info("插件 {} 已回滚到版本 {}", id, target.getVersion());
    logSuccess(extension, "ROLLBACK", 0, "{\"version\":\"" + target.getVersion() + "\"}");
    return true;
  }

  /**
   * 路由探测（simulate 的真实语义）。
   *
   * <p>控制面不持有业务实现类，任何「模拟执行」都只能是伪造结果；因此本方法做的是控制台真实能做的事—— ① 校验插件元数据可发布到运行时；② 读取运行时存储中当前生效的路由决策；③
   * 把探测本身留痕为执行日志（status=PROBE_SUCCESS / PROBE_FAILED）， 与业务进程 SDK 上报的真实执行（INVOKE/SUCCESS/FAILED）区分。
   */
  @Transactional
  public SimulateResult probePluginRouting(Long pluginId) {
    Extension extension = requireExtension(pluginId);
    List<String> validationErrors = List.of();
    boolean valid = true;
    if (!extension.isEnabled()) {
      valid = false;
      validationErrors = List.of("插件未启用（未部署），数据面不会路由到该实现");
    } else if (runtimeSyncService != null) {
      validationErrors = runtimeSyncService.validateForRuntime(extension);
      valid = validationErrors.isEmpty();
    }

    var routing = runtimeSyncService == null ? null : runtimeSyncService.probe(extension);
    boolean published = routing != null;
    ExtPoint extPoint =
        extension.getExtPointId() == null
            ? null
            : extPointRepository.findById(extension.getExtPointId());
    String pointInterface = extPoint == null ? null : extPoint.getInterfaceName();

    String message;
    if (!valid) {
      message = "探测失败：" + String.join("；", validationErrors);
    } else if (published) {
      message = "路由元数据已发布，数据面下次调用将按当前决策路由（traffic=" + routing.getTraffic() + "%）";
    } else {
      message = "元数据校验通过，但路由尚未发布到运行时（请执行部署或发布运行时）";
    }

    logProbe(extension, valid, published, pointInterface, message);

    int priority = extension.getPriority() != null ? extension.getPriority() : 100;
    Integer traffic = routing == null ? null : routing.getTraffic();
    Integer weight = routing == null ? null : routing.getWeight();
    return new SimulateResult(
        extension.getId(),
        extension.getName(),
        extension.getClassName(),
        valid,
        validationErrors,
        published,
        pointInterface,
        routing == null
            ? RuntimeExtensionSyncService.resolveExtensionCode(extension)
            : routing.getCode(),
        routing == null ? extension.getTenantCode() : routing.getTenant(),
        routing == null ? extension.getBizCode() : routing.getBizCode(),
        routing == null ? extension.getUseCase() : routing.getUseCase(),
        routing == null ? extension.getScenario() : routing.getScenario(),
        priority,
        weight,
        traffic,
        extension.isEnabled(),
        message);
  }

  /** 探测留痕：与真实业务执行共用执行日志表，action=PROBE 区分来源。 */
  private void logProbe(
      Extension extension,
      boolean valid,
      boolean published,
      String pointInterface,
      String message) {
    String input;
    String error = valid ? null : message;
    try {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("probe", true);
      node.put("publishedToRuntime", published);
      node.put("extensionPoint", String.valueOf(pointInterface));
      input = objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      input = "{\"probe\":true}";
    }
    executionLogCommandHandler.record(
        extension, "PROBE", valid ? "PROBE_SUCCESS" : "PROBE_FAILED", input, null, error, 0L);
  }

  private void logSuccess(Extension extension, String action, long durationMs, String output) {
    executionLogCommandHandler.record(extension, action, "SUCCESS", null, output, null, durationMs);
  }

  private void logFailure(Extension extension, String action, long durationMs, String error) {
    executionLogCommandHandler.record(extension, action, "FAILED", null, null, error, durationMs);
  }

  @Transactional
  public Extension uploadPluginArtifact(
      MultipartFile file,
      Long extPointId,
      String name,
      String version,
      String className,
      String description,
      Long existingPluginId) {
    try {
      Extension extension;
      if (existingPluginId != null) {
        extension = requireExtension(existingPluginId);
      } else {
        if (extPointId == null || !StringUtils.hasText(name) || !StringUtils.hasText(className)) {
          throw new IllegalArgumentException("新建插件需 extPointId、name、className");
        }
        if (extPointRepository.findById(extPointId) == null) {
          throw StudioErrors.of(StudioErrorCodes.EXT_POINT_NOT_FOUND, extPointId);
        }
        extension = Extension.create(extPointId, name, description, className);
        extension = extensionRepository.save(extension);
      }

      String ver = StringUtils.hasText(version) ? version.trim() : "1.0.0";
      if (pluginVersionRepository.findByPluginVersion(extension.getId(), ver) != null) {
        throw StudioErrors.of(StudioErrorCodes.PLUGIN_VERSION_CONFLICT, ver);
      }

      StoredArtifact artifact = pluginArtifactService.store(extension.getId(), ver, file);
      for (PluginVersion v : pluginVersionReadPort.findByPluginId(extension.getId())) {
        v.setActive(false);
        v.setDeploymentStatus(DeploymentStatus.DEPRECATED);
        pluginVersionRepository.save(v);
      }

      PluginVersion pluginVersion = new PluginVersion();
      pluginVersion.setPluginId(extension.getId());
      pluginVersion.setVersion(ver);
      pluginVersion.setFilePath(artifact.filePath());
      pluginVersion.setFileSize(artifact.fileSize());
      pluginVersion.setChecksum(artifact.checksum());
      pluginVersion.setActive(true);
      pluginVersion.setDeploymentStatus(
          extension.isEnabled() ? DeploymentStatus.ACTIVE : DeploymentStatus.STAGED);
      pluginVersion.setChangeLog("upload");
      pluginVersionRepository.save(pluginVersion);

      pruneOldVersions(extension.getId());
      extension.setConfig(mergeArtifactConfig(extension.getConfig(), pluginVersion));
      extensionRepository.save(extension);
      logSuccess(extension, "UPLOAD", 0, "{\"version\":\"" + ver + "\"}");
      return extension;
    } catch (IOException ex) {
      Extension ext =
          existingPluginId != null ? extensionRepository.findById(existingPluginId) : null;
      if (ext != null) {
        logFailure(ext, "UPLOAD", 0, ex.getMessage());
      }
      throw new IllegalStateException("保存插件包失败: " + ex.getMessage(), ex);
    }
  }

  private void pruneOldVersions(Long pluginId) {
    List<PluginVersion> versions = pluginVersionReadPort.findByPluginId(pluginId);
    int max = pluginArtifactService.getMaxVersionsPerPlugin();
    if (versions.size() <= max) {
      return;
    }
    List<PluginVersion> toRemove =
        versions.stream()
            .sorted(Comparator.comparing(PluginVersion::getCreatedAt).reversed())
            .skip(max)
            .toList();
    for (PluginVersion v : toRemove) {
      if (!v.isActive()) {
        deleteArtifactFile(v.getFilePath());
        pluginVersionRepository.remove(v.getId());
      }
    }
  }

  private void deleteArtifactFile(String filePath) {
    if (!StringUtils.hasText(filePath)) {
      return;
    }
    try {
      Files.deleteIfExists(pluginArtifactService.resolveArtifactPath(filePath));
    } catch (IOException | IllegalArgumentException ex) {
      log.warn("删除插件文件失败: {}", filePath, ex);
    }
  }

  private void markActiveVersionDeployment(Long pluginId, DeploymentStatus status) {
    for (PluginVersion v : pluginVersionReadPort.findByPluginId(pluginId)) {
      if (v.isActive()) {
        v.setDeploymentStatus(status);
        pluginVersionRepository.save(v);
      }
    }
  }

  private String mergeArtifactConfig(String existing, PluginVersion version) {
    try {
      ObjectNode node =
          (existing != null && existing.trim().startsWith("{"))
              ? (ObjectNode) objectMapper.readTree(existing)
              : objectMapper.createObjectNode();
      node.put("jarPath", version.getFilePath());
      node.put("jarChecksum", version.getChecksum());
      node.put("artifactVersion", version.getVersion());
      node.put("jarSize", version.getFileSize());
      return objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      log.warn("合并插件 config JSON 失败，使用最小配置", ex);
      return String.format(
          "{\"jarPath\":\"%s\",\"jarChecksum\":\"%s\",\"artifactVersion\":\"%s\"}",
          version.getFilePath(), version.getChecksum(), version.getVersion());
    }
  }

  private Extension requireExtension(Long id) {
    Extension extension = extensionRepository.findById(id);
    if (extension == null) {
      throw StudioErrors.of(StudioErrorCodes.PLUGIN_NOT_FOUND, id);
    }
    return extension;
  }
}
