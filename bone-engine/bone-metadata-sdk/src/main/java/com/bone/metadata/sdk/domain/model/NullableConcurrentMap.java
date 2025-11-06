package com.bone.metadata.sdk.domain.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NullableConcurrentMap<K, V> implements Map<K, V> {
    private static final Object NULL = new Object();

    private final ConcurrentHashMap<K, Object> internalMap;

    public NullableConcurrentMap() {
        this.internalMap = new ConcurrentHashMap<>();
    }

    public NullableConcurrentMap(int initialCapacity) {
        this.internalMap = new ConcurrentHashMap<>(initialCapacity);
    }

    @SuppressWarnings("unchecked")
    private V maskNull(Object value) {
        return value == NULL ? null : (V) value;
    }

    private Object wrapNull(V value) {
        return value == null ? NULL : value;
    }

    @Override
    public int size() {
        return internalMap.size();
    }

    @Override
    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return internalMap.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsValue(Object value) {
        for (Object v : internalMap.values()) {
            if (Objects.equals(v, wrapNull((V) value))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        return maskNull(internalMap.get(key));
    }

    @Override
    public V put(K key, V value) {
        Object previous = internalMap.put(key, wrapNull(value));
        return maskNull(previous);
    }

    @Override
    public V remove(Object key) {
        return maskNull(internalMap.remove(key));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        internalMap.clear();
    }

    @Override
    public Set<K> keySet() {
        return internalMap.keySet();
    }

    @Override
    public Collection<V> values() {
        Collection<V> result = new ArrayList<>();
        for (Object value : internalMap.values()) {
            result.add(maskNull(value));
        }
        return result;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> result = new HashSet<>();
        for (Entry<K, Object> entry : internalMap.entrySet()) {
            result.add(new AbstractMap.SimpleEntry<>(entry.getKey(), maskNull(entry.getValue())));
        }
        return result;
    }

    public Map<K, V> toMap() {
        Map<K, V> result = new HashMap<>();
        for (Entry<K, Object> entry : internalMap.entrySet()) {
            result.put(entry.getKey(), maskNull(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Map)) return false;
        return this.entrySet().equals(((Map<?, ?>) o).entrySet());
    }

    @Override
    public int hashCode() {
        return this.entrySet().hashCode();
    }

    @Override
    public String toString() {
        return this.toMap().toString();
    }
}