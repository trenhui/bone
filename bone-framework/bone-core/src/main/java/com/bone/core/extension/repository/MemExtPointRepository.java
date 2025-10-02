package com.bone.core.extension.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MemExtPointRepository
 *
 * @author renhui.trh 2023-10-30
 */
public class MemExtPointRepository implements ExtPointRepository {

    private Map<Object, Object> extensionRepo = new ConcurrentHashMap<>();

    /**
     * @param key
     * @return
     */
    @Override
    public Object get(Object key) {
        return extensionRepo.get(key);
    }

    /**
     * @param key
     * @param value
     * @return
     */
    @Override
    public Object put(Object key, Object value) {
        return extensionRepo.put(key, value);
    }

    /**
     * @param key
     * @return
     */
    @Override
    public Object remove(Object key) {
        return extensionRepo.remove(key);
    }

    /**
     *
     */
    @Override
    public void clear() {
        extensionRepo.clear();
    }
}
