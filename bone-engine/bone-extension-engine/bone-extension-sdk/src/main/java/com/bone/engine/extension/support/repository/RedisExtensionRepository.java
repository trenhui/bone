package com.bone.engine.extension.support.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component("redisExtensionRepository")
@ConditionalOnBean(RedisTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class RedisExtensionRepository implements ExtensionRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String PREFIX = "bone:ext:";

    @Override
    public Object put(String key, Object value) {
        String redisKey = PREFIX + key;
        Object old = redisTemplate.opsForValue().get(redisKey);
        redisTemplate.opsForValue().set(redisKey, value);
        log.debug("Repository[REDIS] PUT key={}", key);
        return old;
    }

    @Override
    public Object get(String key) {
        return redisTemplate.opsForValue().get(PREFIX + key);
    }

    @Override
    public Object remove(String key) {
        String redisKey = PREFIX + key;
        Object old = get(key);
        redisTemplate.delete(redisKey);
        return old;
    }

    @Override
    public boolean containsKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + key));
    }

    @Override
    public void clear() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys != null) redisTemplate.delete(keys);
    }

    @Override
    public Set<String> keySet() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys == null) return Set.of();
        return keys.stream()
                .map(k -> k.substring(PREFIX.length()))
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Map<String, Object> getAll() {
        // 生产环境慎用
        return null;
    }

    @Override
    public int size() {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    @Override
    public String getName() {
        return "Redis";
    }
}