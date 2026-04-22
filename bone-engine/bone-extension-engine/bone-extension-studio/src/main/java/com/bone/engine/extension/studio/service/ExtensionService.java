package com.bone.engine.extension.studio.service;

import com.bone.engine.extension.studio.domain.model.Extension;

import java.util.List;
import java.util.Map;

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
}
