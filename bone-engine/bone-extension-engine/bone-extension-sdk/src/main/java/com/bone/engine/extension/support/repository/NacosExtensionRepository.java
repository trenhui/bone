package com.bone.engine.extension.support.repository;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Nacos 扩展点仓库 - 支持配置热加载、集群全节点同步
 */
@Component("nacosExtensionRepository")
@ConditionalOnClass(ConfigService.class)
@Slf4j
public class NacosExtensionRepository implements ExtensionRepository {

    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Nacos 配置
    private static final String DATA_ID = "bone-extension-repo.json";
    private static final String GROUP = "BONE_EXTENSION";
    private static final long TIMEOUT_MS = 3000;

    // 本地缓存结构：扩展点 -> (扩展代码 -> 扩展定义)
    private final Map<String, Map<String, ExtensionDefinition>> storage = new ConcurrentHashMap<>();

    // 异步刷新线程池
    private final Executor executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "nacos-extension-repo-refresher"));

    public NacosExtensionRepository(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    public void init() {
        loadFromNacos();
        subscribeConfigChange();
        log.info("NacosExtensionRepository initialized, DATA_ID={}, GROUP={}", DATA_ID, GROUP);
    }

    @PreDestroy
    public void destroy() {
        try {
            configService.removeConfig(DATA_ID, GROUP);
            configService.shutDown();
        } catch (NacosException e) {
            log.warn("Failed to shutdown Nacos config service", e);
        }
    }

    private void loadFromNacos() {
        try {
            String content = configService.getConfig(DATA_ID, GROUP, TIMEOUT_MS);
            if (content != null && !content.trim().isEmpty()) {
                Map<String, Map<String, ExtensionDefinition>> data = objectMapper.readValue(content,
                        new TypeReference<Map<String, Map<String, ExtensionDefinition>>>() {});
                storage.clear();
                storage.putAll(data);
                log.info("Loaded {} extension points from Nacos", storage.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load extension repo from Nacos, using empty storage", e);
        }
    }

    private void subscribeConfigChange() {
        try {
            configService.addListener(DATA_ID, GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return executor;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    try {
                        if (configInfo == null || configInfo.trim().isEmpty()) {
                            storage.clear();
                            log.info("Nacos extension repo cleared by remote config");
                            return;
                        }

                        Map<String, Map<String, ExtensionDefinition>> newData = objectMapper.readValue(configInfo,
                                new TypeReference<Map<String, Map<String, ExtensionDefinition>>>() {});
                        storage.clear();
                        storage.putAll(newData);
                        log.info("Nacos extension repo refreshed, {} extension points loaded", storage.size());
                    } catch (Exception e) {
                        log.error("Failed to parse Nacos extension repo config", e);
                    }
                }
            });
        } catch (NacosException e) {
            log.error("Failed to subscribe Nacos config change", e);
        }
    }

    private void publishToNacos() {
        try {
            String content = objectMapper.writeValueAsString(storage);
            boolean success = configService.publishConfig(DATA_ID, GROUP, content);
            if (success) {
                log.debug("Extension repo published to Nacos, {} extension points", storage.size());
            }
        } catch (Exception e) {
            log.error("Failed to publish extension repo to Nacos", e);
        }
    }

    @Override
    @Nullable
    public ExtensionDefinition register(@NonNull String extensionPoint, @NonNull ExtensionDefinition extension) {
        Map<String, ExtensionDefinition> pointExtensions = storage.computeIfAbsent(
                extensionPoint, k -> new ConcurrentHashMap<>());

        ExtensionDefinition previous = pointExtensions.put(extension.getCode(), extension);
        publishToNacos();

        log.debug("Extension registered in Nacos: {} -> {}", extensionPoint, extension.getCode());
        return previous;
    }

    @Override
    @Nullable
    public ExtensionDefinition unregister(@NonNull String extensionPoint, @NonNull String extensionCode) {
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        if (pointExtensions != null) {
            ExtensionDefinition removed = pointExtensions.remove(extensionCode);
            if (removed != null) {
                // 清理空的扩展点
                if (pointExtensions.isEmpty()) {
                    storage.remove(extensionPoint);
                }
                publishToNacos();
                log.debug("Extension unregistered from Nacos: {} -> {}", extensionPoint, extensionCode);
            }
            return removed;
        }
        return null;
    }

    @Override
    @Nullable
    public ExtensionDefinition unregisterByCode(@NonNull String extensionCode) {
        for (Map.Entry<String, Map<String, ExtensionDefinition>> entry : storage.entrySet()) {
            ExtensionDefinition removed = entry.getValue().remove(extensionCode);
            if (removed != null) {
                // 清理空的扩展点
                if (entry.getValue().isEmpty()) {
                    storage.remove(entry.getKey());
                }
                publishToNacos();
                log.debug("Extension globally unregistered from Nacos: {}", extensionCode);
                return removed;
            }
        }
        return null;
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint) {
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
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
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null ?
                Collections.unmodifiableCollection(new ArrayList<>(pointExtensions.values())) :
                Collections.emptyList();
    }

    @Override
    @Nullable
    public ExtensionDefinition getExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
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
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
        return pointExtensions != null && pointExtensions.containsKey(extensionCode);
    }

    @Override
    public boolean hasExtensions(@NonNull String extensionPoint) {
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
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
        Map<String, ExtensionDefinition> removed = storage.remove(extensionPoint);
        if (removed != null) {
            publishToNacos();
            log.debug("Cleared {} extensions from Nacos point: {}", removed.size(), extensionPoint);
            return removed.size();
        }
        return 0;
    }

    @Override
    public void clearAll() {
        storage.clear();
        publishToNacos();
        log.debug("All extensions cleared from Nacos repository");
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
        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
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
                System.currentTimeMillis(),
                "Nacos"
        );
    }

    @Override
    @NonNull
    public String getName() {
        return "Nacos";
    }

    @Override
    @NonNull
    public String getType() {
        return "Nacos";
    }
}