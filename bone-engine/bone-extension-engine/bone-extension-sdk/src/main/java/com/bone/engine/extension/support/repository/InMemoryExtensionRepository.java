package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 基于内存的扩展点仓库实现
 *
 * <p>特性： 1. 线程安全：基于ConcurrentHashMap 2. 高性能：O(1)的读写操作 3. 内存友好：使用紧凑的数据结构 4. 监控支持：内置统计信息
 */
@Component("inMemoryExtensionRepository")
@Slf4j
public class InMemoryExtensionRepository implements ExtensionRepository {

  // 核心存储结构：扩展点 -> (扩展代码 -> 扩展定义)
  private final ConcurrentMap<String, ConcurrentMap<String, ExtensionDefinition>> storage;
  private final AtomicLong lastModifiedTime;
  private final String name;
  private final String type;

  public InMemoryExtensionRepository() {
    this("InMemoryExtensionRepository");
  }

  public InMemoryExtensionRepository(String name) {
    this.storage = new ConcurrentHashMap<>(64);
    this.lastModifiedTime = new AtomicLong(System.currentTimeMillis());
    this.name = name;
    this.type = "InMemory";
  }

  @Override
  @Nullable
  public ExtensionDefinition registerExtension(
      @NonNull String extensionPoint, @NonNull ExtensionDefinition extension) {
    Objects.requireNonNull(extensionPoint, "Extension point cannot be null");
    Objects.requireNonNull(extension, "Extension definition cannot be null");

    ConcurrentMap<String, ExtensionDefinition> pointExtensions =
        storage.computeIfAbsent(extensionPoint, k -> new ConcurrentHashMap<>(16));

    ExtensionDefinition previous = pointExtensions.put(extension.getCode(), extension);
    lastModifiedTime.set(System.currentTimeMillis());

    log.debug("Extension registered: {} -> {}", extensionPoint, extension.getCode());
    return previous;
  }

  @Override
  @Nullable
  public ExtensionDefinition unregisterExtension(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    if (pointExtensions != null) {
      ExtensionDefinition removed = pointExtensions.remove(extensionCode);
      if (removed != null) {
        lastModifiedTime.set(System.currentTimeMillis());
        // 清理空的扩展点
        if (pointExtensions.isEmpty()) {
          storage.remove(extensionPoint);
        }
        log.debug("Extension unregistered: {} -> {}", extensionPoint, extensionCode);
      }
      return removed;
    }
    return null;
  }

  @Override
  @NonNull
  public Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    if (pointExtensions == null) {
      return Collections.emptyList();
    }

    return pointExtensions.values().stream()
        .filter(ExtensionDefinition::isEnabled)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  @NonNull
  public Collection<ExtensionDefinition> getAllExtensions(@NonNull String extensionPoint) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    return pointExtensions != null
        ? Collections.unmodifiableCollection(new ArrayList<>(pointExtensions.values()))
        : Collections.emptyList();
  }

  @Override
  @NonNull
  public Optional<ExtensionDefinition> getExtensionByCode(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    if (pointExtensions != null) {
      return Optional.ofNullable(pointExtensions.get(extensionCode));
    }
    return Optional.empty();
  }

  @Override
  public int batchRegisterExtensions(
      @NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint) {
    int count = 0;
    for (Map.Entry<String, Collection<ExtensionDefinition>> entry : extensionsByPoint.entrySet()) {
      String extensionPoint = entry.getKey();
      for (ExtensionDefinition extension : entry.getValue()) {
        registerExtension(extensionPoint, extension);
        count++;
      }
    }
    return count;
  }

  @Override
  public int clearExtensionPoint(@NonNull String extensionPoint) {
    ConcurrentMap<String, ExtensionDefinition> removed = storage.remove(extensionPoint);
    if (removed != null) {
      lastModifiedTime.set(System.currentTimeMillis());
      log.debug("Cleared {} extensions from point: {}", removed.size(), extensionPoint);
      return removed.size();
    }
    return 0;
  }

  @Override
  public void clearAllExtensions() {
    storage.clear();
    lastModifiedTime.set(System.currentTimeMillis());
    log.debug("All extensions cleared from repository");
  }

  @Override
  @NonNull
  public Set<String> getAllExtensionPointNames() {
    return Collections.unmodifiableSet(new HashSet<>(storage.keySet()));
  }

  @Override
  public boolean hasExtensions(@NonNull String extensionPoint) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    return pointExtensions != null && !pointExtensions.isEmpty();
  }

  @Override
  @NonNull
  public ExtensionRepositoryStats getRepositoryStats() {
    int totalExtensionPoints = storage.size();
    int totalExtensions = storage.values().stream().mapToInt(Map::size).sum();

    int enabledExtensions =
        storage.values().stream()
            .mapToInt(
                extMap ->
                    (int) extMap.values().stream().filter(ExtensionDefinition::isEnabled).count())
            .sum();

    return new ExtensionRepositoryStats(
        name, totalExtensionPoints, totalExtensions, enabledExtensions, lastModifiedTime.get());
  }

  // ==================== 辅助方法（非接口方法）====================

  /** 全局按扩展码查找扩展（非接口方法，内部使用） */
  @Nullable
  public ExtensionDefinition findExtensionByCodeGlobally(@NonNull String extensionCode) {
    return storage.values().stream()
        .map(extensions -> extensions.get(extensionCode))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /** 检查扩展码是否已注册（非接口方法） */
  public boolean isExtensionRegistered(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    return pointExtensions != null && pointExtensions.containsKey(extensionCode);
  }

  /** 统计指定扩展点的扩展数量（非接口方法） */
  public int countExtensionsInPoint(@NonNull String extensionPoint) {
    ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
    return pointExtensions != null ? pointExtensions.size() : 0;
  }

  /** 获取仓库名称 */
  public String getRepositoryName() {
    return name;
  }

  /** 获取仓库类型 */
  public String getRepositoryType() {
    return type;
  }
}
