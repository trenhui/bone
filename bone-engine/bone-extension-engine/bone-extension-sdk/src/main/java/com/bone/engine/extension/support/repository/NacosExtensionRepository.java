//package com.bone.engine.extension.support.repository;
//
//import com.alibaba.nacos.api.config.ConfigService;
//import com.alibaba.nacos.api.config.listener.Listener;
//import com.alibaba.nacos.api.exception.NacosException;
//import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.bone.engine.extension.api.spi.ExtensionRepository;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.lang.NonNull;
//import org.springframework.lang.Nullable;
//
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//import java.util.stream.Collectors;
//
///**
// * Nacos 扩展点仓库 - 支持配置热加载、集群全节点同步
// */
//@Slf4j
//public class NacosExtensionRepository implements ExtensionRepository {
//
//    private final ConfigService configService;
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    // Nacos 配置
//    private static final String DATA_ID = "bone-extension-repo.json";
//    private static final String GROUP = "BONE_EXTENSION";
//    private static final long TIMEOUT_MS = 3000;
//
//    // 本地缓存结构：扩展点 -> (扩展代码 -> 扩展定义)
//    private final Map<String, Map<String, ExtensionDefinition>> storage = new ConcurrentHashMap<>();
//
//    // 异步刷新线程池
//    private final Executor executor = Executors.newSingleThreadExecutor(
//            r -> new Thread(r, "nacos-extension-repo-refresher"));
//
//    public NacosExtensionRepository(ConfigService configService) {
//        this.configService = configService;
//    }
//
//    @PostConstruct
//    public void init() {
//        loadFromNacos();
//        subscribeConfigChange();
//        log.info("NacosExtensionRepository initialized, DATA_ID={}, GROUP={}", DATA_ID, GROUP);
//    }
//
//    @PreDestroy
//    public void destroy() {
//        try {
//            configService.removeConfig(DATA_ID, GROUP);
//            configService.shutDown();
//        } catch (NacosException e) {
//            log.warn("Failed to shutdown Nacos config service", e);
//        }
//    }
//
//    private void loadFromNacos() {
//        try {
//            String content = configService.getConfig(DATA_ID, GROUP, TIMEOUT_MS);
//            if (content != null && !content.trim().isEmpty()) {
//                Map<String, Map<String, ExtensionDefinition>> data = objectMapper.readValue(content,
//                        new TypeReference<Map<String, Map<String, ExtensionDefinition>>>() {});
//                storage.clear();
//                storage.putAll(data);
//                log.info("Loaded {} extension points from Nacos", storage.size());
//            }
//        } catch (Exception e) {
//            log.warn("Failed to load extension repo from Nacos, using empty storage", e);
//        }
//    }
//
//    private void subscribeConfigChange() {
//        try {
//            configService.addListener(DATA_ID, GROUP, new Listener() {
//                @Override
//                public Executor getExecutor() {
//                    return executor;
//                }
//
//                @Override
//                public void receiveConfigInfo(String configInfo) {
//                    try {
//                        if (configInfo == null || configInfo.trim().isEmpty()) {
//                            storage.clear();
//                            log.info("Nacos extension repo cleared by remote config");
//                            return;
//                        }
//
//                        Map<String, Map<String, ExtensionDefinition>> newData = objectMapper.readValue(configInfo,
//                                new TypeReference<Map<String, Map<String, ExtensionDefinition>>>() {});
//                        storage.clear();
//                        storage.putAll(newData);
//                        log.info("Nacos extension repo refreshed, {} extension points loaded", storage.size());
//                    } catch (Exception e) {
//                        log.error("Failed to parse Nacos extension repo config", e);
//                    }
//                }
//            });
//        } catch (NacosException e) {
//            log.error("Failed to subscribe Nacos config change", e);
//        }
//    }
//
//    private void publishToNacos() {
//        try {
//            String content = objectMapper.writeValueAsString(storage);
//            boolean success = configService.publishConfig(DATA_ID, GROUP, content);
//            if (success) {
//                log.debug("Extension repo published to Nacos, {} extension points", storage.size());
//            }
//        } catch (Exception e) {
//            log.error("Failed to publish extension repo to Nacos", e);
//        }
//    }
//
//    @Override
//    @Nullable
//    public ExtensionDefinition registerExtension(@NonNull String extensionPoint, @NonNull ExtensionDefinition extension) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.computeIfAbsent(
//                extensionPoint, k -> new ConcurrentHashMap<>());
//
//        ExtensionDefinition previous = pointExtensions.put(extension.getCode(), extension);
//        publishToNacos();
//
//        log.debug("Extension registered in Nacos: {} -> {}", extensionPoint, extension.getCode());
//        return previous;
//    }
//
//    @Override
//    @Nullable
//    public ExtensionDefinition unregisterExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        if (pointExtensions != null) {
//            ExtensionDefinition removed = pointExtensions.remove(extensionCode);
//            if (removed != null) {
//                // 清理空的扩展点
//                if (pointExtensions.isEmpty()) {
//                    storage.remove(extensionPoint);
//                }
//                publishToNacos();
//                log.debug("Extension unregistered from Nacos: {} -> {}", extensionPoint, extensionCode);
//            }
//            return removed;
//        }
//        return null;
//    }
//
//    @Override
//    @NonNull
//    public Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        if (pointExtensions == null) {
//            return Collections.emptyList();
//        }
//
//        return pointExtensions.values().stream()
//                .filter(ExtensionDefinition::isEnabled)
//                .collect(Collectors.toUnmodifiableList());
//    }
//
//    @Override
//    @NonNull
//    public Collection<ExtensionDefinition> getAllExtensions(@NonNull String extensionPoint) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        return pointExtensions != null ?
//                Collections.unmodifiableCollection(new ArrayList<>(pointExtensions.values())) :
//                Collections.emptyList();
//    }
//
//    @Override
//    @NonNull
//    public Optional<ExtensionDefinition> getExtensionByCode(@NonNull String extensionPoint, @NonNull String extensionCode) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        if (pointExtensions != null) {
//            return Optional.ofNullable(pointExtensions.get(extensionCode));
//        }
//        return Optional.empty();
//    }
//
//    @Override
//    public int batchRegisterExtensions(@NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint) {
//        int count = 0;
//        for (Map.Entry<String, Collection<ExtensionDefinition>> entry : extensionsByPoint.entrySet()) {
//            String extensionPoint = entry.getKey();
//            for (ExtensionDefinition extension : entry.getValue()) {
//                registerExtension(extensionPoint, extension);
//                count++;
//            }
//        }
//        return count;
//    }
//
//    @Override
//    public int clearExtensionPoint(@NonNull String extensionPoint) {
//        Map<String, ExtensionDefinition> removed = storage.remove(extensionPoint);
//        if (removed != null) {
//            publishToNacos();
//            log.debug("Cleared {} extensions from Nacos point: {}", removed.size(), extensionPoint);
//            return removed.size();
//        }
//        return 0;
//    }
//
//    @Override
//    public void clearAllExtensions() {
//        storage.clear();
//        publishToNacos();
//        log.debug("All extensions cleared from Nacos repository");
//    }
//
//    @Override
//    @NonNull
//    public Set<String> getAllExtensionPointNames() {
//        return Collections.unmodifiableSet(new HashSet<>(storage.keySet()));
//    }
//
//    @Override
//    public boolean hasExtensions(@NonNull String extensionPoint) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        return pointExtensions != null && !pointExtensions.isEmpty();
//    }
//
//    @Override
//    @NonNull
//    public ExtensionRepositoryStats getRepositoryStats() {
//        int totalExtensionPoints = storage.size();
//        int totalExtensions = storage.values().stream()
//                .mapToInt(Map::size)
//                .sum();
//
//        int enabledExtensions = storage.values().stream()
//                .mapToInt(extMap -> (int) extMap.values().stream()
//                        .filter(ExtensionDefinition::isEnabled)
//                        .count())
//                .sum();
//
//        return new ExtensionRepositoryStats(
//                "Nacos",
//                totalExtensionPoints,
//                totalExtensions,
//                enabledExtensions,
//                System.currentTimeMillis()
//        );
//    }
//
//    // ==================== 辅助方法（非接口方法）====================
//
//    /**
//     * 全局按扩展码查找扩展（非接口方法，内部使用）
//     */
//    @Nullable
//    public ExtensionDefinition findExtensionByCodeGlobally(@NonNull String extensionCode) {
//        return storage.values().stream()
//                .map(extensions -> extensions.get(extensionCode))
//                .filter(Objects::nonNull)
//                .findFirst()
//                .orElse(null);
//    }
//
//    /**
//     * 检查扩展码是否已注册（非接口方法）
//     */
//    public boolean isExtensionRegistered(@NonNull String extensionPoint, @NonNull String extensionCode) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        return pointExtensions != null && pointExtensions.containsKey(extensionCode);
//    }
//
//    /**
//     * 统计指定扩展点的扩展数量（非接口方法）
//     */
//    public int countExtensionsInPoint(@NonNull String extensionPoint) {
//        Map<String, ExtensionDefinition> pointExtensions = storage.get(extensionPoint);
//        return pointExtensions != null ? pointExtensions.size() : 0;
//    }
//
//    /**
//     * 获取仓库名称
//     */
//    public String getRepositoryName() {
//        return "Nacos";
//    }
//
//    /**
//     * 获取仓库类型
//     */
//    public String getRepositoryType() {
//        return "Nacos";
//    }
//}