package com.bone.engine.extension.studio.service;

import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.model.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface ExtensionService {
    List<Extension> findAllExtensions();
    Extension findExtensionById(Long id);
    List<Extension> findExtensionsByExtPointId(Long extPointId);
    Extension saveExtension(Extension extension);
    Extension updateExtension(Long id, Extension extension);
    void deleteExtension(Long id);
    Extension enableExtension(Long id, boolean enabled);
    Extension updateExtensionPriority(Long id, int priority);
    List<Extension> findExtensionsByTenantCode(String tenantCode);
    List<Extension> searchExtensions(String keyword);
    int registerExtensions();
    long getTotalExtensionCount();
    Map<String, Long> getExtensionStatsByStatus();
    Map<String, Long> getExtensionStatsByExtPoint();
    boolean validateExtension(Extension extension);
    String getExtensionStatistics(Long id);
    void resetExtensionStatistics(Long id);

    boolean deployExtension(Long id);

    boolean undeployExtension(Long id);

    boolean publishRuntime(Long id);

    boolean rollbackExtension(Long id);

    boolean rollbackExtension(Long id, String version);

    List<PluginVersion> listPluginVersions(Long pluginId);

    Extension uploadPluginArtifact(
            MultipartFile file,
            Long extPointId,
            String name,
            String version,
            String className,
            String description,
            Long existingPluginId);

    /** 模拟插件调用并写入执行日志（联调/演示）。 */
    PluginExecutionLog simulatePluginExecution(Long pluginId);
}
