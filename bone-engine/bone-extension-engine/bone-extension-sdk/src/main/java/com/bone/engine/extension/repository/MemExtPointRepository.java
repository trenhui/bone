package com.bone.engine.extension.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存扩展点仓库实现，提供线程安全的扩展点存储和管理
 *
 * @author renhui.trh 2023-10-30
 */

public class MemExtPointRepository implements ExtPointRepository {
    private static final Logger log = LoggerFactory.getLogger(MemExtPointRepository.class);
    // 使用ConcurrentHashMap保证线程安全
    private final Map<Object, Object> extensionRepo = new ConcurrentHashMap<>();

    /**
     * 获取扩展点实例
     *
     * @param key 扩展点标识
     * @return 扩展点实例，如果不存在则返回null
     * @throws IllegalArgumentException 当key为null时抛出
     */
    @Override
    public Object get(Object key) {
        Assert.notNull(key, "Extension key must not be null");
        return extensionRepo.get(key);
    }

    /**
     * 存储扩展点实例
     *
     * @param key   扩展点标识
     * @param value 扩展点实例
     * @return 之前关联的值，如果不存在则返回null
     * @throws IllegalArgumentException 当key或value为null时抛出
     */
    @Override
    public Object put(Object key, Object value) {
        Assert.notNull(key, "Extension key must not be null");
        Assert.notNull(value, "Extension value must not be null");
        
        log.debug("Registering extension with key: {}, value type: {}", key, value.getClass().getName());
        return extensionRepo.put(key, value);
    }

    /**
     * 移除扩展点实例
     *
     * @param key 扩展点标识
     * @return 被移除的值，如果不存在则返回null
     * @throws IllegalArgumentException 当key为null时抛出
     */
    @Override
    public Object remove(Object key) {
        Assert.notNull(key, "Extension key must not be null");
        
        log.debug("Removing extension with key: {}", key);
        return extensionRepo.remove(key);
    }

    /**
     * 清空所有扩展点
     */
    @Override
    public void clear() {
        log.info("Clearing all extensions, current size: {}", extensionRepo.size());
        extensionRepo.clear();
    }
    
    /**
     * 获取当前存储的扩展点数量
     * 
     * @return 扩展点数量
     */
    public int size() {
        return extensionRepo.size();
    }
    
    /**
     * 检查是否包含指定key的扩展点
     * 
     * @param key 扩展点标识
     * @return 如果存在则返回true，否则返回false
     * @throws IllegalArgumentException 当key为null时抛出
     */
    public boolean containsKey(Object key) {
        Assert.notNull(key, "Extension key must not be null");
        return extensionRepo.containsKey(key);
    }
    
    /**
     * 获取所有扩展点的键集合
     * 
     * @return 包含所有扩展点键的集合
     */
    public Set<Object> keySet() {
        return extensionRepo.keySet();
    }
}
