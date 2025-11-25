package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Redis 扩展点仓库实现
 */
@Component("redisExtensionRepository")
@ConditionalOnBean(RedisTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class RedisExtensionRepository implements ExtensionRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EXTENSION_POINT_PREFIX = "bone:ext:point:";
    private static final String EXTENSION_KEY_PREFIX = "bone:ext:";
    private static final String EXTENSION_POINTS_KEY = "bone:ext:points";

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
        try {
            String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extension.getCode();
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;

            // 存储扩展定义
            Object previous = redisTemplate.opsForValue().get(extensionKey);
            redisTemplate.opsForValue().set(extensionKey, extension);

            // 添加到扩展点集合
            redisTemplate.opsForSet().add(pointKey, extension.getCode());

            // 记录扩展点
            redisTemplate.opsForSet().add(EXTENSION_POINTS_KEY, extensionPoint);

            log.debug("Extension registered in Redis: {} -> {}", extensionPoint, extension.getCode());
            return previous != null ? (ExtensionDefinition) previous : null;
        } catch (Exception e) {
            log.error("Failed to register extension in Redis: {} -> {}", extensionPoint, extension.getCode(), e);
            return null;
        }
    }

    @Override
    @Nullable
    public ExtensionDefinition unregister(@NonNull String extensionPoint, @NonNull String extensionCode) {
        try {
            String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extensionCode;
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;

            // 获取并删除扩展定义
            ExtensionDefinition previous = (ExtensionDefinition) redisTemplate.opsForValue().get(extensionKey);
            redisTemplate.delete(extensionKey);

            // 从扩展点集合中移除
            redisTemplate.opsForSet().remove(pointKey, extensionCode);

            // 如果扩展点为空，清理扩展点记录
            Long size = redisTemplate.opsForSet().size(pointKey);
            if (size != null && size == 0) {
                redisTemplate.delete(pointKey);
                redisTemplate.opsForSet().remove(EXTENSION_POINTS_KEY, extensionPoint);
            }

            log.debug("Extension unregistered from Redis: {} -> {}", extensionPoint, extensionCode);
            return previous;
        } catch (Exception e) {
            log.error("Failed to unregister extension from Redis: {} -> {}", extensionPoint, extensionCode, e);
            return null;
        }
    }

    @Override
    @Nullable
    public ExtensionDefinition unregisterByCode(@NonNull String extensionCode) {
        try {
            // 搜索所有扩展点中的该扩展代码
            Set<String> extensionPoints = getExtensionPointNames();
            for (String extensionPoint : extensionPoints) {
                ExtensionDefinition removed = unregister(extensionPoint, extensionCode);
                if (removed != null) {
                    log.debug("Extension globally unregistered from Redis: {}", extensionCode);
                    return removed;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to globally unregister extension from Redis: {}", extensionCode, e);
            return null;
        }
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint) {
        return getAllExtensions(extensionPoint).stream()
                .filter(ExtensionDefinition::isEnabled)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> getAllExtensions(@NonNull String extensionPoint) {
        try {
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;
            Set<Object> extensionCodes = redisTemplate.opsForSet().members(pointKey);

            if (extensionCodes == null || extensionCodes.isEmpty()) {
                return Collections.emptyList();
            }

            List<ExtensionDefinition> extensions = new ArrayList<>();
            for (Object codeObj : extensionCodes) {
                String extensionCode = (String) codeObj;
                String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extensionCode;
                ExtensionDefinition extension = (ExtensionDefinition) redisTemplate.opsForValue().get(extensionKey);
                if (extension != null) {
                    extensions.add(extension);
                }
            }

            return Collections.unmodifiableList(extensions);
        } catch (Exception e) {
            log.error("Failed to get extensions from Redis for point: {}", extensionPoint, e);
            return Collections.emptyList();
        }
    }

    @Override
    @Nullable
    public ExtensionDefinition getExtension(@NonNull String extensionPoint, @NonNull String extensionCode) {
        try {
            String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extensionCode;
            return (ExtensionDefinition) redisTemplate.opsForValue().get(extensionKey);
        } catch (Exception e) {
            log.error("Failed to get extension from Redis: {} -> {}", extensionPoint, extensionCode, e);
            return null;
        }
    }

    @Override
    @Nullable
    public ExtensionDefinition getExtensionByCode(@NonNull String extensionCode) {
        try {
            Set<String> extensionPoints = getExtensionPointNames();
            for (String extensionPoint : extensionPoints) {
                ExtensionDefinition extension = getExtension(extensionPoint, extensionCode);
                if (extension != null) {
                    return extension;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get extension by code from Redis: {}", extensionCode, e);
            return null;
        }
    }

    @Override
    public boolean isRegistered(@NonNull String extensionPoint, @NonNull String extensionCode) {
        try {
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(pointKey, extensionCode));
        } catch (Exception e) {
            log.error("Failed to check extension registration in Redis: {} -> {}", extensionPoint, extensionCode, e);
            return false;
        }
    }

    @Override
    public boolean hasExtensions(@NonNull String extensionPoint) {
        try {
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;
            Long size = redisTemplate.opsForSet().size(pointKey);
            return size != null && size > 0;
        } catch (Exception e) {
            log.error("Failed to check extensions in Redis for point: {}", extensionPoint, e);
            return false;
        }
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
        try {
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;
            Set<Object> extensionCodes = redisTemplate.opsForSet().members(pointKey);

            if (extensionCodes == null || extensionCodes.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (Object codeObj : extensionCodes) {
                String extensionCode = (String) codeObj;
                String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extensionCode;
                redisTemplate.delete(extensionKey);
                count++;
            }

            redisTemplate.delete(pointKey);
            redisTemplate.opsForSet().remove(EXTENSION_POINTS_KEY, extensionPoint);

            log.debug("Cleared {} extensions from Redis point: {}", count, extensionPoint);
            return count;
        } catch (Exception e) {
            log.error("Failed to clear extensions from Redis for point: {}", extensionPoint, e);
            return 0;
        }
    }

    @Override
    public void clearAll() {
        try {
            Set<String> extensionPoints = getExtensionPointNames();
            for (String extensionPoint : extensionPoints) {
                clearExtensions(extensionPoint);
            }
            redisTemplate.delete(EXTENSION_POINTS_KEY);
            log.debug("All extensions cleared from Redis repository");
        } catch (Exception e) {
            log.error("Failed to clear all extensions from Redis", e);
        }
    }

    @Override
    @NonNull
    public Collection<ExtensionDefinition> findExtensions(@NonNull Predicate<ExtensionDefinition> condition) {
        try {
            Set<String> extensionPoints = getExtensionPointNames();
            List<ExtensionDefinition> allExtensions = new ArrayList<>();

            for (String extensionPoint : extensionPoints) {
                allExtensions.addAll(getAllExtensions(extensionPoint));
            }

            return allExtensions.stream()
                    .filter(condition)
                    .collect(Collectors.toUnmodifiableList());
        } catch (Exception e) {
            log.error("Failed to find extensions in Redis", e);
            return Collections.emptyList();
        }
    }

    @Override
    public int countExtensionPoints() {
        try {
            Long size = redisTemplate.opsForSet().size(EXTENSION_POINTS_KEY);
            return size != null ? size.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to count extension points in Redis", e);
            return 0;
        }
    }

    @Override
    public int countExtensions() {
        try {
            Set<String> extensionPoints = getExtensionPointNames();
            int total = 0;
            for (String extensionPoint : extensionPoints) {
                String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;
                Long size = redisTemplate.opsForSet().size(pointKey);
                if (size != null) {
                    total += size.intValue();
                }
            }
            return total;
        } catch (Exception e) {
            log.error("Failed to count extensions in Redis", e);
            return 0;
        }
    }

    @Override
    public int countExtensionsInPoint(@NonNull String extensionPoint) {
        try {
            String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;
            Long size = redisTemplate.opsForSet().size(pointKey);
            return size != null ? size.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to count extensions in Redis for point: {}", extensionPoint, e);
            return 0;
        }
    }

    @Override
    @NonNull
    public Set<String> getExtensionPointNames() {
        try {
            Set<Object> points = redisTemplate.opsForSet().members(EXTENSION_POINTS_KEY);
            return points != null ?
                    points.stream().map(String::valueOf).collect(Collectors.toSet()) :
                    Collections.emptySet();
        } catch (Exception e) {
            log.error("Failed to get extension point names from Redis", e);
            return Collections.emptySet();
        }
    }

    @Override
    @NonNull
    public RepositoryStats getStats() {
        return new RepositoryStats(
                countExtensionPoints(),
                countExtensions(),
                System.currentTimeMillis(),
                "Redis"
        );
    }

    @Override
    @NonNull
    public String getName() {
        return "Redis";
    }

    @Override
    @NonNull
    public String getType() {
        return "Redis";
    }
}