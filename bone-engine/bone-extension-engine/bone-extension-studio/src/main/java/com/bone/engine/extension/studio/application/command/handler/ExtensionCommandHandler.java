package com.bone.engine.extension.studio.application.command.handler;

import com.bone.engine.extension.studio.application.service.PluginArtifactService;
import com.bone.engine.extension.studio.application.service.PluginArtifactService.StoredArtifact;
import com.bone.engine.extension.studio.application.service.StudioVersionSupport;
import com.bone.engine.extension.studio.application.service.StudioPatchSupport;
import com.bone.engine.extension.studio.domain.model.DeploymentStatus;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import java.util.Map;
import com.bone.engine.extension.studio.domain.gateway.PluginVersionReadPort;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.domain.repository.PluginVersionRepository;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ExtensionCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ExtensionCommandHandler.class);

    private final ExtensionRepository extensionRepository;
    private final ExtPointRepository extPointRepository;
    private final PluginVersionRepository pluginVersionRepository;
    private final PluginVersionReadPort pluginVersionReadPort;
    private final PluginArtifactService pluginArtifactService;
    private final PluginExecutionLogCommandHandler executionLogCommandHandler;

    @Autowired(required = false)
    private RuntimeExtensionSyncService runtimeSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Extension saveExtension(Extension extension) {
        if (extension.getExtPointId() != null && extPointRepository.findById(extension.getExtPointId()) == null) {
            throw new IllegalArgumentException("关联扩展点不存在: " + extension.getExtPointId());
        }
        if (extension.getVersion() == null) {
            extension.setVersion(0);
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
        if (existing.getExtPointId() != null && extPointRepository.findById(existing.getExtPointId()) == null) {
            throw new IllegalArgumentException("关联扩展点不存在: " + existing.getExtPointId());
        }
        existing.setVersion(StudioVersionSupport.nextVersion(existing.getVersion()));
        extensionRepository.save(existing);
        return existing;
    }

    @Transactional
    public Extension updateExtension(Long id, Extension extension, Integer expectedVersion) {
        Extension existing = extensionRepository.findById(id);
        if (existing == null) {
            return null;
        }
        com.bone.engine.extension.studio.application.service.StudioVersionSupport.assertExpected(
                expectedVersion, existing.getVersion());
        if (extension.getExtPointId() != null) {
            existing.setExtPointId(extension.getExtPointId());
        }
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
                com.bone.engine.extension.studio.application.service.StudioVersionSupport.nextVersion(existing.getVersion()));
        extensionRepository.save(existing);
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
        return extension;
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
            markActiveVersionDeployment(id, DeploymentStatus.ACTIVE);
            boolean published = publishRuntime(id);
            logSuccess(extension, "DEPLOY", System.currentTimeMillis() - start, "{\"published\":" + published + "}");
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
                throw new IllegalStateException("插件未启用，无法发布到运行时");
            }
            if (runtimeSyncService == null) {
                log.warn("RuntimeExtensionSyncService 未配置，跳过运行时发布");
                logSuccess(extension, "PUBLISH_RUNTIME", System.currentTimeMillis() - start, "{\"skipped\":true}");
                return true;
            }
            List<String> errors = runtimeSyncService.validateForRuntime(extension);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(String.join("; ", errors));
            }
            boolean ok = runtimeSyncService.publish(extension);
            logSuccess(extension, "PUBLISH_RUNTIME", System.currentTimeMillis() - start, "{\"published\":" + ok + "}");
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
            throw new IllegalArgumentException("插件无可用版本");
        }

        PluginVersion target;
        if (StringUtils.hasText(version)) {
            target = pluginVersionRepository.findByPluginVersion(id, version.trim());
            if (target == null) {
                throw new IllegalArgumentException("版本不存在: " + version);
            }
        } else {
            PluginVersion active = pluginVersionReadPort.findActiveByPluginId(id);
            target = versions.stream()
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
    @Transactional
    public PluginExecutionLog simulatePluginExecution(Long pluginId) {
        Extension extension = requireExtension(pluginId);
        if (!extension.isEnabled()) {
            throw new IllegalStateException("请先部署插件后再模拟调用");
        }
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(20 + (long) (Math.random() * 80));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long duration = System.currentTimeMillis() - start;
        return executionLogCommandHandler.record(
                extension,
                "INVOKE",
                "SUCCESS",
                "{\"bizCode\":\"" + extension.getBizCode() + "\"}",
                "{\"result\":\"ok\"}",
                null,
                duration);
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
                    throw new IllegalArgumentException("关联扩展点不存在: " + extPointId);
                }
                extension = Extension.create(extPointId, name, description, className);
                extension = extensionRepository.save(extension);
            }

            String ver = StringUtils.hasText(version) ? version.trim() : "1.0.0";
            if (pluginVersionRepository.findByPluginVersion(extension.getId(), ver) != null) {
                throw new IllegalArgumentException("版本已存在: " + ver);
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
            Extension ext = existingPluginId != null ? extensionRepository.findById(existingPluginId) : null;
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
        List<PluginVersion> toRemove = versions.stream()
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
            throw new IllegalArgumentException("插件不存在: " + id);
        }
        return extension;
    }
}
