package com.bone.engine.extension.repository;

/**
 * ExtPointRepository
 *
 * @author renhui.trh 2023-10-30
 */
public interface ExtPointRepository {

    Object get(Object key);

    Object put(Object key, Object value);

    Object remove(Object key);

    void clear();
}
