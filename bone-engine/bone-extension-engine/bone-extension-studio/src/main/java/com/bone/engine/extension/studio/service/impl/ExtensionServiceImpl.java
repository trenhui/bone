package com.bone.engine.extension.studio.service.impl;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import com.bone.engine.extension.studio.domain.store.ExtPointStore;
import com.bone.engine.extension.studio.domain.store.ExtensionStore;
import com.bone.engine.extension.studio.domain.store.PluginVersionStore;
import com.bone.engine.extension.studio.service.ExtensionService;
import com.bone.engine.extension.studio.service.PluginArtifactService;
import com.bone.engine.extension.studio.service.PluginExecutionLogService;
import com.bone.engine.extension.studio.service.PluginArtifactService.StoredArtifact;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExtensionServiceImpl implements ExtensionService {

    private static final Logger log = LoggerFactory.getLogger(ExtensionServiceImpl.class);

    @Autowired
    private ExtensionStore extensionStore;

    @Autowired
    private ExtPointStore extPointStore;

    @Autowired(required = false)
    private RuntimeExtensionSyncService runtimeSyncService;

    @Autowired
    private PluginVersionStore pluginVersionStore;

    @Autowired
    private PluginArtifactService pluginArtifactService;

    @Autowired
    private PluginExecutionLogService executionLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Extension> findAllExtensions() {
        return extensionStore.findAll();
    }

    @Override
    public Extension findExtensionById(Long id) {
        return extensionStore.findById(id);
    }

    @Override
    public List<Extension> findExtensionsByExtPointId(Long extPointId) {
        return extensionStore.findByExtPointId(extPointId);
    }

    @Override
    public Extension saveExtension(Extension extension) {
        if (extension.getExtPointId() != null && extPointStore.findById(extension.getExtPointId()) == null) {
            throw new IllegalArgumentException("关联扩展点不存在: " + extension.getExtPointId());
        }
        if (extension.getVersion() == null) {
            extension.setVersion(0);
        }
        return extensionStore.save(extension);
    }

    @Override
    public Extension updateExtension(Long id, Extension extension) {
        return updateExtension(id, extension, null);
    }

    @Override
    public Extension updateExtension(Long id, Extension extension, Integer expectedVersion) {
        Extension existing = extensionStore.findById(id);
        if (existing == null) {
            return null;
        }
        com.bone.engine.extension.studio.service.StudioVersionSupport.assertExpected(
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
                com.bone.engine.extension.studio.service.StudioVersionSupport.nextVersion(existing.getVersion()));
        extensionStore.update(existing);
        return existing;
    }

    @Override
    public void deleteExtension(Long id) {
        Extension extension = extensionStore.findById(id);
        if (extension != null && runtimeSyncService != null) {
            ExtPoint point = extPointStore.findById(extension.getExtPointId());
            runtimeSyncService.unpublish(extension, point);
        }
        for (PluginVersion v : pluginVersionStore.findByPluginId(id)) {
            deleteArtifactFile(v.getFilePath());
        }
        pluginVersionStore.deleteByPluginId(id);
        extensionStore.deleteById(id);
    }

    @Override
    public Extension enableExtension(Long id, boolean enabled) {
        Extension extension = extensionStore.findById(id);
        if (extension == null) {
            return null;
        }
        extension.setEnabled(enabled);
        extensionStore.update(extension);
        return extension;
    }

    @Override
    public Extension updateExtensionPriority(Long id, int priority) {
        Extension extension = extensionStore.findById(id);
        if (extension == null) {
            return null;
        }
        extension.setPriority(priority);
        extensionStore.update(extension);
        return extension;
    }

    @Override
    public List<Extension> findExtensionsByTenantCode(String tenantCode) {
        return extensionStore.findByTenantCode(tenantCode);
    }

    @Override
    public List<Extension> searchExtensions(String keyword) {
        return extensionStore.search(keyword);
    }

    @Override
    public int registerExtensions() {
        log.info("扩展实现 classpath 扫描暂未接入，返回 0");
        return 0;
    }

    @Override
    public long getTotalExtensionCount() {
        return extensionStore.count();
    }

    @Override
    public Map<String, Long> getExtensionStatsByStatus() {
        Map<String, Long> stats = new HashMap<>();
        long enabled =
                extensionStore.findAll().stream().filter(Extension::isEnabled).count();
        stats.put("enabled", enabled);
        stats.put("disabled", extensionStore.count() - enabled);
        return stats;
    }

    @Override
    public Map<String, Long> getExtensionStatsByExtPoint() {
        return extensionStore.findAll().stream()
                .collect(Collectors.groupingBy(
                        e -> e.getExtPointId() == null ? "unknown" : String.valueOf(e.getExtPointId()),
                        Collectors.counting()));
    }

    @Override
    public boolean validateExtension(Extension extension) {
        if (runtimeSyncService == null) {
            return StringUtils.hasText(extension.getClassName()) && extension.getExtPointId() != null;
        }
        return runtimeSyncService.validateForRuntime(extension).isEmpty();
    }

    @Override
    public String getExtensionStatistics(Long id) {
        Extension extension = extensionStore.findById(id);
        if (extension == null) {
            return "{}";
        }
        return String.format(
                "{\"id\":%d,\"enabled\":%s,\"priority\":%d}",
                extension.getId(), extension.isEnabled(), extension.getPriority());
    }

    @Override
    public void resetExtensionStatistics(Long id) {
        log.debug("resetExtensionStatistics id={}", id);
    }

    @Override
    public boolean deployExtension(Long id) {
        long start = System.currentTimeMillis();
        Extension extension = requireExtension(id);
        try {
            extension.enable();
            extensionStore.update(extension);
            boolean published = publishRuntime(id);
            logSuccess(extension, "DEPLOY", System.currentTimeMillis() - start, "{\"published\":" + published + "}");
            return published;
        } catch (RuntimeException ex) {
            logFailure(extension, "DEPLOY", System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public boolean undeployExtension(Long id) {
        long start = System.currentTimeMillis();
        Extension extension = requireExtension(id);
        try {
            if (runtimeSyncService != null) {
                ExtPoint point = extPointStore.findById(extension.getExtPointId());
                runtimeSyncService.unpublish(extension, point);
            }
            extension.disable();
            extensionStore.update(extension);
            logSuccess(extension, "UNDEPLOY", System.currentTimeMillis() - start, null);
            return true;
        } catch (RuntimeException ex) {
            logFailure(extension, "UNDEPLOY", System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
    }

    @Override
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

    @Override
    public boolean rollbackExtension(Long id) {
        return rollbackExtension(id, null);
    }

    @Override
    public boolean rollbackExtension(Long id, String version) {
        Extension extension = requireExtension(id);
        List<PluginVersion> versions = pluginVersionStore.findByPluginId(id);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("插件无可用版本");
        }

        PluginVersion target;
        if (StringUtils.hasText(version)) {
            target = pluginVersionStore.findByPluginIdAndVersion(id, version.trim());
            if (target == null) {
                throw new IllegalArgumentException("版本不存在: " + version);
            }
        } else {
            PluginVersion active = pluginVersionStore.findActiveByPluginId(id);
            target = versions.stream()
                    .filter(v -> active == null || !v.getId().equals(active.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("没有可回滚的上一个版本"));
        }

        boolean wasEnabled = extension.isEnabled();
        if (wasEnabled && runtimeSyncService != null) {
            ExtPoint point = extPointStore.findById(extension.getExtPointId());
            runtimeSyncService.unpublish(extension, point);
        }

        for (PluginVersion v : versions) {
            v.setActive(v.getId().equals(target.getId()));
            pluginVersionStore.save(v);
        }

        extension.setConfig(mergeArtifactConfig(extension.getConfig(), target));
        extensionStore.update(extension);

        if (wasEnabled) {
            publishRuntime(id);
        }
        log.info("插件 {} 已回滚到版本 {}", id, target.getVersion());
        logSuccess(extension, "ROLLBACK", 0, "{\"version\":\"" + target.getVersion() + "\"}");
        return true;
    }

    @Override
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
        return executionLogService.record(
                extension,
                "INVOKE",
                "SUCCESS",
                "{\"bizCode\":\"" + extension.getBizCode() + "\"}",
                "{\"result\":\"ok\"}",
                null,
                duration);
    }

    private void logSuccess(Extension extension, String action, long durationMs, String output) {
        executionLogService.record(extension, action, "SUCCESS", null, output, null, durationMs);
    }

    private void logFailure(Extension extension, String action, long durationMs, String error) {
        executionLogService.record(extension, action, "FAILED", null, null, error, durationMs);
    }

    @Override
    public List<PluginVersion> listPluginVersions(Long pluginId) {
        requireExtension(pluginId);
        return pluginVersionStore.findByPluginId(pluginId);
    }

    @Override
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
                if (extPointStore.findById(extPointId) == null) {
                    throw new IllegalArgumentException("关联扩展点不存在: " + extPointId);
                }
                extension = Extension.create(extPointId, name, description, className);
                extension = extensionStore.save(extension);
            }

            String ver = StringUtils.hasText(version) ? version.trim() : "1.0.0";
            if (pluginVersionStore.findByPluginIdAndVersion(extension.getId(), ver) != null) {
                throw new IllegalArgumentException("版本已存在: " + ver);
            }

            StoredArtifact artifact = pluginArtifactService.store(extension.getId(), ver, file);
            for (PluginVersion v : pluginVersionStore.findByPluginId(extension.getId())) {
                v.setActive(false);
                pluginVersionStore.save(v);
            }

            PluginVersion pluginVersion = new PluginVersion();
            pluginVersion.setPluginId(extension.getId());
            pluginVersion.setVersion(ver);
            pluginVersion.setFilePath(artifact.filePath());
            pluginVersion.setFileSize(artifact.fileSize());
            pluginVersion.setChecksum(artifact.checksum());
            pluginVersion.setActive(true);
            pluginVersion.setChangeLog("upload");
            pluginVersionStore.save(pluginVersion);

            pruneOldVersions(extension.getId());
            extension.setConfig(mergeArtifactConfig(extension.getConfig(), pluginVersion));
            extensionStore.update(extension);
            logSuccess(extension, "UPLOAD", 0, "{\"version\":\"" + ver + "\"}");
            return extension;
        } catch (IOException ex) {
            Extension ext = existingPluginId != null ? extensionStore.findById(existingPluginId) : null;
            if (ext != null) {
                logFailure(ext, "UPLOAD", 0, ex.getMessage());
            }
            throw new IllegalStateException("保存插件包失败: " + ex.getMessage(), ex);
        }
    }

    private void pruneOldVersions(Long pluginId) {
        List<PluginVersion> versions = pluginVersionStore.findByPluginId(pluginId);
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
                pluginVersionStore.deleteById(v.getId());
            }
        }
    }

    private void deleteArtifactFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException ex) {
            log.warn("删除插件文件失败: {}", filePath, ex);
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
        Extension extension = extensionStore.findById(id);
        if (extension == null) {
            throw new IllegalArgumentException("插件不存在: " + id);
        }
        return extension;
    }
}
