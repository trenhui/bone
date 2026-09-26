package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
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
public class InMemoryStudioExtensionRepository implements ExtensionRepository, ExtensionReadPort {

  private final Map<Long, Extension> storage = new ConcurrentHashMap<>();
  private final AtomicLong idSequence = new AtomicLong(1);

  @Override
  public List<Extension> findAll() {
    return storage.values().stream()
        .sorted(Comparator.comparing(Extension::getId, Comparator.nullsLast(Long::compareTo)))
        .collect(Collectors.toList());
  }

  @Override
  @Nullable
  public Extension findById(Long id) {
    return id == null ? null : storage.get(id);
  }

  @Override
  @Nullable
  public Extension findByClassName(String className) {
    if (className == null || className.isBlank()) {
      return null;
    }
    return storage.values().stream()
        .filter(e -> className.equals(e.getClassName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public List<Extension> findByExtPointId(Long extPointId) {
    if (extPointId == null) {
      return List.of();
    }
    return storage.values().stream()
        .filter(e -> extPointId.equals(e.getExtPointId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Extension> findByTenantCode(String tenantCode) {
    if (tenantCode == null) {
      return List.of();
    }
    return storage.values().stream()
        .filter(e -> tenantCode.equals(e.getTenantCode()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Extension> findByAppId(Long appId) {
    if (appId == null) {
      return List.of();
    }
    return storage.values().stream()
        .filter(e -> appId.equals(e.getAppId()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Extension> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return findAll();
    }
    String lower = keyword.toLowerCase();
    return storage.values().stream()
        .filter(
            e ->
                contains(e.getName(), lower)
                    || contains(e.getClassName(), lower)
                    || contains(e.getDescription(), lower))
        .collect(Collectors.toList());
  }

  @Override
  public Extension save(Extension extension) {
    if (extension.getId() == null) {
      extension.setId(idSequence.getAndIncrement());
    }
    storage.put(extension.getId(), extension);
    return extension;
  }

  @Override
  public boolean remove(Long id) {
    return id != null && storage.remove(id) != null;
  }

  @Override
  public long count() {
    return storage.size();
  }

  private static boolean contains(@Nullable String value, String lowerKeyword) {
    return value != null && value.toLowerCase().contains(lowerKeyword);
  }
}
