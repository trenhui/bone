package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/** Redis 扩展点仓库实现 */
@Component("redisExtensionRepository")
@ConditionalOnBean(RedisTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class RedisExtensionRepository implements ExtensionRepository {

  private final RedisTemplate<String, Object> redisTemplate;

  private static final String EXTENSION_POINT_PREFIX = "bone:ext:point:";
  private static final String EXTENSION_KEY_PREFIX = "bone:ext:";
  private static final String EXTENSION_POINTS_KEY = "bone:ext:points";

  /**
   * 注册扩展实现到指定扩展点
   *
   * @param extensionPoint 扩展点全限定名
   * @param extension 扩展定义
   * @return 如果扩展代码已存在，返回已注册的定义，否则返回null
   */
  @Override
  @Nullable
  public ExtensionDefinition registerExtension(
      @NonNull String extensionPoint, @NonNull ExtensionDefinition extension) {
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
      log.error(
          "Failed to register extension in Redis: {} -> {}",
          extensionPoint,
          extension.getCode(),
          e);
      return null;
    }
  }

  @Override
  @Nullable
  public ExtensionDefinition unregisterExtension(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    try {
      String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extensionCode;
      String pointKey = EXTENSION_POINT_PREFIX + extensionPoint;

      // 获取并删除扩展定义
      ExtensionDefinition previous =
          (ExtensionDefinition) redisTemplate.opsForValue().get(extensionKey);
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
      log.error(
          "Failed to unregister extension from Redis: {} -> {}", extensionPoint, extensionCode, e);
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
        ExtensionDefinition extension =
            (ExtensionDefinition) redisTemplate.opsForValue().get(extensionKey);
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
  @NonNull
  public Optional<ExtensionDefinition> getExtensionByCode(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    try {
      String extensionKey = EXTENSION_KEY_PREFIX + extensionPoint + ":" + extensionCode;
      ExtensionDefinition extension =
          (ExtensionDefinition) redisTemplate.opsForValue().get(extensionKey);
      return Optional.ofNullable(extension);
    } catch (Exception e) {
      log.error("Failed to get extension from Redis: {} -> {}", extensionPoint, extensionCode, e);
      return Optional.empty();
    }
  }

  @Override
  public int batchRegisterExtensions(
      @NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint) {
    int count = 0;
    for (Map.Entry<String, Collection<ExtensionDefinition>> entry : extensionsByPoint.entrySet()) {
      String extensionPoint = entry.getKey();
      for (ExtensionDefinition extension : entry.getValue()) {
        registerExtension(extensionPoint, extension);
        count++;
      }
    }
    return count;
  }

  @Override
  public int clearExtensionPoint(@NonNull String extensionPoint) {
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
  public void clearAllExtensions() {
    try {
      Set<String> extensionPoints = getAllExtensionPointNames();
      for (String extensionPoint : extensionPoints) {
        clearExtensionPoint(extensionPoint);
      }
      redisTemplate.delete(EXTENSION_POINTS_KEY);
      log.debug("All extensions cleared from Redis repository");
    } catch (Exception e) {
      log.error("Failed to clear all extensions from Redis", e);
    }
  }

  @Override
  @NonNull
  public Set<String> getAllExtensionPointNames() {
    try {
      Set<Object> points = redisTemplate.opsForSet().members(EXTENSION_POINTS_KEY);
      return points != null
          ? points.stream().map(String::valueOf).collect(Collectors.toSet())
          : Collections.emptySet();
    } catch (Exception e) {
      log.error("Failed to get extension point names from Redis", e);
      return Collections.emptySet();
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
  @NonNull
  public ExtensionRepositoryStats getRepositoryStats() {
    int totalExtensionPoints = 0;
    int totalExtensions = 0;
    int enabledExtensions = 0;

    try {
      Set<String> extensionPoints = getAllExtensionPointNames();
      totalExtensionPoints = extensionPoints.size();

      for (String extensionPoint : extensionPoints) {
        Collection<ExtensionDefinition> extensions = getAllExtensions(extensionPoint);
        totalExtensions += extensions.size();
        enabledExtensions +=
            (int) extensions.stream().filter(ExtensionDefinition::isEnabled).count();
      }
    } catch (Exception e) {
      log.error("Failed to get repository stats from Redis", e);
    }

    return new ExtensionRepositoryStats(
        "Redis",
        totalExtensionPoints,
        totalExtensions,
        enabledExtensions,
        System.currentTimeMillis());
  }
}
