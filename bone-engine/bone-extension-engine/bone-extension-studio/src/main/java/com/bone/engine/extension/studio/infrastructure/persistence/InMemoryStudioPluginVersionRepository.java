package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.PluginVersionReadPort;
import com.bone.engine.extension.studio.domain.model.PluginVersion;
import com.bone.engine.extension.studio.domain.repository.PluginVersionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
    prefix = "bone.extension.studio.persistence",
    name = "mode",
    havingValue = "in-memory",
    matchIfMissing = true)
public class InMemoryStudioPluginVersionRepository
    implements PluginVersionRepository, PluginVersionReadPort {

  private final Map<Long, PluginVersion> storage = new ConcurrentHashMap<>();
  private final AtomicLong idSequence = new AtomicLong(1);

  @Override
  public List<PluginVersion> findByPluginId(Long pluginId) {
    return storage.values().stream()
        .filter(v -> pluginId.equals(v.getPluginId()))
        .sorted(Comparator.comparing(PluginVersion::getCreatedAt).reversed())
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public PluginVersion findByPluginVersion(Long pluginId, String version) {
    return storage.values().stream()
        .filter(v -> pluginId.equals(v.getPluginId()) && version.equals(v.getVersion()))
        .findFirst()
        .orElse(null);
  }

  @Override
  @Nullable
  public PluginVersion findActiveByPluginId(Long pluginId) {
    return storage.values().stream()
        .filter(v -> pluginId.equals(v.getPluginId()) && v.isActive())
        .findFirst()
        .orElse(null);
  }

  @Override
  public PluginVersion save(PluginVersion version) {
    if (version.getId() == null) {
      version.setId(idSequence.getAndIncrement());
    }
    storage.put(version.getId(), version);
    return version;
  }

  @Override
  public boolean remove(Long id) {
    return id != null && storage.remove(id) != null;
  }
}
