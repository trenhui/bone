package com.bone.engine.extension.studio.service.impl;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.store.ExtPointStore;
import com.bone.engine.extension.studio.domain.store.ExtensionStore;
import com.bone.engine.extension.studio.service.ExtensionService;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExtensionServiceImpl implements ExtensionService {

    private static final Logger log = LoggerFactory.getLogger(ExtensionServiceImpl.class);

    @Autowired
    private ExtensionStore extensionStore;

    @Autowired
    private ExtPointStore extPointStore;

    @Autowired(required = false)
    private RuntimeExtensionSyncService runtimeSyncService;

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
        return extensionStore.save(extension);
    }

    @Override
    public Extension updateExtension(Long id, Extension extension) {
        Extension existing = extensionStore.findById(id);
        if (existing == null) {
            return null;
        }
        if (extension.getExtPointId() != null) {
            existing.setExtPointId(extension.getExtPointId());
        }
        existing.setName(extension.getName());
        existing.setDescription(extension.getDescription());
        existing.setClassName(extension.getClassName());
        existing.setTenantCode(extension.getTenantCode());
        existing.setBizCode(extension.getBizCode());
        existing.setUseCase(extension.getUseCase());
        existing.setScenario(extension.getScenario());
        existing.setUserGroup(extension.getUserGroup());
        existing.setPriority(extension.getPriority());
        existing.setConfig(extension.getConfig());
        existing.setEnabled(extension.isEnabled());
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

    public boolean deployExtension(Long id) {
        Extension extension = requireExtension(id);
        extension.enable();
        extensionStore.update(extension);
        return publishRuntime(id);
    }

    public boolean undeployExtension(Long id) {
        Extension extension = requireExtension(id);
        if (runtimeSyncService != null) {
            ExtPoint point = extPointStore.findById(extension.getExtPointId());
            runtimeSyncService.unpublish(extension, point);
        }
        extension.disable();
        extensionStore.update(extension);
        return true;
    }

    public boolean publishRuntime(Long id) {
        Extension extension = requireExtension(id);
        if (!extension.isEnabled()) {
            throw new IllegalStateException("插件未启用，无法发布到运行时");
        }
        if (runtimeSyncService == null) {
            log.warn("RuntimeExtensionSyncService 未配置，跳过运行时发布");
            return true;
        }
        List<String> errors = runtimeSyncService.validateForRuntime(extension);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return runtimeSyncService.publish(extension);
    }

    public boolean rollbackExtension(Long id) {
        Extension extension = requireExtension(id);
        if (runtimeSyncService != null) {
            ExtPoint point = extPointStore.findById(extension.getExtPointId());
            runtimeSyncService.unpublish(extension, point);
        }
        extension.disable();
        extensionStore.update(extension);
        log.info("插件 {} 已回滚为禁用状态（版本表能力待接入）", id);
        return true;
    }

    private Extension requireExtension(Long id) {
        Extension extension = extensionStore.findById(id);
        if (extension == null) {
            throw new IllegalArgumentException("插件不存在: " + id);
        }
        return extension;
    }
}
