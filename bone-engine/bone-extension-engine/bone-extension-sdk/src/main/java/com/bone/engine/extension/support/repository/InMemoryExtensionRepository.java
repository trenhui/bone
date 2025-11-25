package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 基于内存的扩展点仓库实现
 *
 * 特性：
 * 1. 线程安全：基于ConcurrentHashMap
 * 2. 高性能：O(1)的读写操作
 * 3. 内存友好：使用紧凑的数据结构
 * 4. 监控支持：内置统计信息
 */
@Component("inMemoryExtensionRepository")
//@ConditionalOnClass(com.alibaba.nacos.api.config.ConfigService.class)
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

    /**
     * 注册扩展实现到指定扩展点
     *
     * @param extensionPoint 扩展点全限定名
     * @param extension      扩展定义
     * @return 如果扩展代码已存在，返回已注册的定义，否则返回null
     */
    @Override
    public ExtensionDefinition register(String extensionPoint, Object extension) {
        return null;
    }

    @Override
    @Nullable
    public ExtensionDefinition register(@NonNull String extensionPoint, @NonNull ExtensionDefinition extension) {
        Objects.requireNonNull(extensionPoint, "Extension point cannot be null");
        Objects.requireNonNull(extension, "Extension definition cannot be null");

        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.computeIfAbsent(
                extensionPoint, k -> new ConcurrentHashMap<>(16));

        ExtensionDefinition previous = pointExtensions.put(extension.getCode(), extension);
        lastModifiedTime.set(System.currentTimeMillis());

        log.debug("Extension registered: {} -> {}", extensionPoint, extension.getCode());
        return previous;
    }

    @Override
    @Nullable
    public ExtensionDefinition unregister(@NonNull String extensionPoint, @NonNull String extensionCode) {
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
    @Nullable
    public ExtensionDefinition unregisterByCode(@NonNull String extensionCode) {
        for (Map.Entry<String, ConcurrentMap<String, ExtensionDefinition>> entry : storage.entrySet()) {
            ExtensionDefinition removed = entry.getValue().remove(extensionCode);
            if (removed != null) {
                lastModifiedTime.set(System.currentTimeMillis());
                // 清理空的扩展点
                if (entry.getValue().isEmpty()) {
                    storage.remove(entry.getKey());
                }
                log.debug("Extension globally unregistered: {}", extensionCode);
                return removed;
            }
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
        return pointExtensions != null ?
                Collections.unmodifiableCollection(new ArrayList<>(pointExtensions.values())) :
                Collections.emptyList();
    }

    @Override
    @Nullable
    public ExtensionDefinition getExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null ? pointExtensions.get(extensionCode) : null;
    }

    @Override
    @Nullable
    public ExtensionDefinition getExtensionByCode(@NonNull String extensionCode) {
        return storage.values().stream()
                .map(extensions -> extensions.get(extensionCode))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean isRegistered(@NonNull String extensionPoint, @NonNull String extensionCode) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null && pointExtensions.containsKey(extensionCode);
    }

    @Override
    public boolean hasExtensions(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null && !pointExtensions.isEmpty();
    }

    @Override
    public int registerAll(@NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint) {
        int count = 0;
        for (Map.Entry<String, Collection<ExtensionDefinition>> entry : extensionsByPoint.entrySet()) {
            String extensionPoint = entry.getKey();
            for (ExtensionDefinition extension : entry.getValue()) {
                register(extensionPoint, extension);
                count++;
            }
        }
        return count;
    }

    @Override
    public int clearExtensions(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> removed = storage.remove(extensionPoint);
        if (removed != null) {
            lastModifiedTime.set(System.currentTimeMillis());
            log.debug("Cleared {} extensions from point: {}", removed.size(), extensionPoint);
            return removed.size();
        }
        return 0;
    }

    @Override
    public void clearAll() {
        storage.clear();
        lastModifiedTime.set(System.currentTimeMillis());
        log.debug("All extensions cleared from repository");
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> findExtensions(@NonNull Predicate<ExtensionDefinition> condition) {
        return storage.values().stream()
                .flatMap(extensions -> extensions.values().stream())
                .filter(condition)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int countExtensionPoints() {
        return storage.size();
    }

    @Override
    public int countExtensions() {
        return storage.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    @Override
    public int countExtensionsInPoint(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null ? pointExtensions.size() : 0;
    }

    @Override
    @NonNull
    public Set<String> getExtensionPointNames() {
        return Collections.unmodifiableSet(new HashSet<>(storage.keySet()));
    }

    @Override
    @NonNull
    public RepositoryStats getStats() {
        return new RepositoryStats(
                countExtensionPoints(),
                countExtensions(),
                lastModifiedTime.get(),
                name
        );
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String getType() {
        return type;
    }
}