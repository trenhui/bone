package com.bone.engine.extension.studio.application.query.handler;

import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.gateway.PluginVersionReadPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.plugin.PluginVersion;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 插件读侧。 */
@Component
@RequiredArgsConstructor
public class ExtensionQueryApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ExtensionQueryApplicationService.class);

  private final ExtensionReadPort extensionReadPort;
  private final ExtensionRepository extensionRepository;
  private final PluginVersionReadPort pluginVersionReadPort;

  @Autowired(required = false)
  private RuntimeExtensionSyncService runtimeSyncService;

  public List<Extension> findAllExtensions() {
    return extensionReadPort.findAll();
  }

  public Extension findExtensionById(Long id) {
    return extensionRepository.findById(id);
  }

  public List<Extension> findExtensionsByExtPointId(Long extPointId) {
    return extensionReadPort.findByExtPointId(extPointId);
  }

  public List<Extension> findExtensionsByTenantCode(String tenantCode) {
    return extensionReadPort.findByTenantCode(tenantCode);
  }

  public List<Extension> searchExtensions(String keyword) {
    return extensionReadPort.search(keyword);
  }

  public int registerExtensions() {
    log.info("扩展实现 classpath 扫描暂未接入，返回 0");
    return 0;
  }

  public long getTotalExtensionCount() {
    return extensionReadPort.count();
  }

  public Map<String, Long> getExtensionStatsByStatus() {
    Map<String, Long> stats = new HashMap<>();
    long enabled = extensionReadPort.findAll().stream().filter(Extension::isEnabled).count();
    stats.put("enabled", enabled);
    stats.put("disabled", extensionReadPort.count() - enabled);
    return stats;
  }

  public Map<String, Long> getExtensionStatsByExtPoint() {
    return extensionReadPort.findAll().stream()
        .collect(
            Collectors.groupingBy(
                e -> e.getExtPointId() == null ? "unknown" : String.valueOf(e.getExtPointId()),
                Collectors.counting()));
  }

  public boolean validateExtension(Extension extension) {
    if (runtimeSyncService == null) {
      return StringUtils.hasText(extension.getClassName()) && extension.getExtPointId() != null;
    }
    return runtimeSyncService.validateForRuntime(extension).isEmpty();
  }

  public String getExtensionStatistics(Long id) {
    Extension extension = extensionRepository.findById(id);
    if (extension == null) {
      return "{}";
    }
    return String.format(
        "{\"id\":%d,\"enabled\":%s,\"priority\":%d}",
        extension.getId(), extension.isEnabled(), extension.getPriority());
  }

  public List<PluginVersion> listPluginVersions(Long pluginId) {
    if (extensionRepository.findById(pluginId) == null) {
      throw new IllegalArgumentException("插件不存在: " + pluginId);
    }
    return pluginVersionReadPort.findByPluginId(pluginId);
  }
}
