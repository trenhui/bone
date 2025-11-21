package com.bone.engine.extension.support.repository;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos 扩展点仓库 - 支持配置热加载、集群全节点 1s 同步
 *
 * @author Bone Engine Team
 * @since 2.0.0-GA 2025-11-21
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

    // 本地缓存（最终一致性）
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

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
            if (content != null) {
                Map<String, Object> data = objectMapper.readValue(content,
                        new TypeReference<Map<String, Object>>() {});
                cache.putAll(data);
                log.info("Loaded {} extensions from Nacos", cache.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load extension repo from Nacos, using empty cache", e);
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
                            cache.clear();
                            log.info("Nacos extension repo cleared by remote config");
                            return;
                        }

                        Map<String, Object> newData = objectMapper.readValue(configInfo,
                                new TypeReference<Map<String, Object>>() {});
                        cache.clear();
                        cache.putAll(newData);
                        log.info("Nacos extension repo refreshed, {} items loaded", cache.size());
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
            String content = objectMapper.writeValueAsString(cache);
            boolean success = configService.publishConfig(DATA_ID, GROUP, content);
            if (success) {
                log.debug("Extension repo published to Nacos, size={}", cache.size());
            }
        } catch (Exception e) {
            log.error("Failed to publish extension repo to Nacos", e);
        }
    }

    @Override
    public Object put(String key, Object value) {
        Object old = cache.put(key, value);
        publishToNacos();
        return old;
    }

    @Override
    public Object get(String key) {
        return cache.get(key);
    }

    @Override
    public Object remove(String key) {
        Object removed = cache.remove(key);
        publishToNacos();
        return removed;
    }

    @Override
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }

    @Override
    public void clear() {
        cache.clear();
        publishToNacos();
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    @Override
    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public String getName() {
        return "Nacos";
    }
}