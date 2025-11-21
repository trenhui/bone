package com.bone.engine.extension.support.repository;

import com.alibaba.nacos.api.config.ConfigService;
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
@ConditionalOnClass(ConfigService.class)
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
    public ExtensionDefinition save(@NonNull String extensionPoint, @NonNull ExtensionDefinition definition) {
        Objects.requireNonNull(extensionPoint, "Extension point cannot be null");
        Objects.requireNonNull(definition, "Extension definition cannot be null");

        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.computeIfAbsent(
                extensionPoint, k -> new ConcurrentHashMap<>(16));

        ExtensionDefinition previous = pointExtensions.put(definition.getCode(), definition);
        lastModifiedTime.set(System.currentTimeMillis());

        return previous;
    }

    @Override
    @Nullable
    public ExtensionDefinition delete(@NonNull String extensionPoint, @NonNull String extensionCode) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        if (pointExtensions != null) {
            ExtensionDefinition removed = pointExtensions.remove(extensionCode);
            if (removed != null) {
                lastModifiedTime.set(System.currentTimeMillis());
                // 清理空的扩展点
                if (pointExtensions.isEmpty()) {
                    storage.remove(extensionPoint);
                }
            }
            return removed;
        }
        return null;
    }

    @Override
    @Nullable
    public ExtensionDefinition deleteByCode(@NonNull String extensionCode) {
        for (Map.Entry<String, ConcurrentMap<String, ExtensionDefinition>> entry : storage.entrySet()) {
            ExtensionDefinition removed = entry.getValue().remove(extensionCode);
            if (removed != null) {
                lastModifiedTime.set(System.currentTimeMillis());
                // 清理空的扩展点
                if (entry.getValue().isEmpty()) {
                    storage.remove(entry.getKey());
                }
                return removed;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public ExtensionDefinition findByPointAndCode(@NonNull String extensionPoint, @NonNull String extensionCode) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null ? pointExtensions.get(extensionCode) : null;
    }

    @Override
    @Nullable
    public ExtensionDefinition findByCode(@NonNull String extensionCode) {
        return storage.values().stream()
                .map(extensions -> extensions.get(extensionCode))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> findAllByPoint(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null ?
                Collections.unmodifiableCollection(new ArrayList<>(pointExtensions.values())) :
                Collections.emptyList();
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> findAll() {
        return storage.values().stream()
                .flatMap(extensions -> extensions.values().stream())
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> findByCondition(@NonNull Predicate<ExtensionDefinition> predicate) {
        return storage.values().stream()
                .flatMap(extensions -> extensions.values().stream())
                .filter(predicate)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> findEnabledByPoint(@NonNull String extensionPoint) {
        return findAllByPoint(extensionPoint).stream()
                .filter(ExtensionDefinition::isEnabled)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean exists(@NonNull String extensionPoint, @NonNull String extensionCode) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null && pointExtensions.containsKey(extensionCode);
    }

    @Override
    public boolean existsByPoint(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null && !pointExtensions.isEmpty();
    }

    @Override
    public int saveAll(@NonNull Map<String, Collection<ExtensionDefinition>> definitions) {
        int count = 0;
        for (Map.Entry<String, Collection<ExtensionDefinition>> entry : definitions.entrySet()) {
            String extensionPoint = entry.getKey();
            for (ExtensionDefinition definition : entry.getValue()) {
                save(extensionPoint, definition);
                count++;
            }
        }
        return count;
    }

    @Override
    public int clearByPoint(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> removed = storage.remove(extensionPoint);
        if (removed != null) {
            lastModifiedTime.set(System.currentTimeMillis());
            return removed.size();
        }
        return 0;
    }

    @Override
    public int countPoints() {
        return storage.size();
    }

    @Override
    public int countExtensions() {
        return storage.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    @Override
    public int countByPoint(@NonNull String extensionPoint) {
        ConcurrentMap<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null ? pointExtensions.size() : 0;
    }

    @Override
    @NonNull
    public Set<String> getAllExtensionPoints() {
        return Collections.unmodifiableSet(new HashSet<>(storage.keySet()));
    }

    @Override
    public void clear() {
        storage.clear();
        lastModifiedTime.set(System.currentTimeMillis());
    }

    @Override
    @NonNull
    public RepositoryStatistics getStatistics() {
        return new RepositoryStatistics(
                countPoints(),
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