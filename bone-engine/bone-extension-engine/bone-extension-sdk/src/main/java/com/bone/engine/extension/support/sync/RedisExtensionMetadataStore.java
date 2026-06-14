package com.bone.engine.extension.support.sync;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/** Redis 元数据存储（Studio 与业务进程共享，Jackson 序列化值 + String 索引集）。 */
@Slf4j
public class RedisExtensionMetadataStore implements ExtensionMetadataStore {

  private final RedisTemplate<String, ExtensionRoutingMetadata> valueTemplate;
  private final StringRedisTemplate indexTemplate;
  private final String refreshChannel;

  public RedisExtensionMetadataStore(
      RedisTemplate<String, ExtensionRoutingMetadata> valueTemplate,
      StringRedisTemplate indexTemplate) {
    this(valueTemplate, indexTemplate, ExtensionMetadataKeys.REFRESH_CHANNEL);
  }

  public RedisExtensionMetadataStore(
      RedisTemplate<String, ExtensionRoutingMetadata> valueTemplate,
      StringRedisTemplate indexTemplate,
      @NonNull String refreshChannel) {
    this.valueTemplate = valueTemplate;
    this.indexTemplate = indexTemplate;
    this.refreshChannel =
        StringUtils.hasText(refreshChannel)
            ? refreshChannel
            : ExtensionMetadataKeys.REFRESH_CHANNEL;
  }

  @Override
  public void save(@NonNull ExtensionRoutingMetadata metadata) {
    String key =
        ExtensionMetadataKeys.metadataKey(metadata.getExtensionPoint(), metadata.getCode());
    valueTemplate.opsForValue().set(key, metadata);
    indexTemplate
        .opsForSet()
        .add(ExtensionMetadataKeys.indexKey(metadata.getExtensionPoint()), metadata.getCode());
    log.debug("Saved extension metadata to Redis: {}", key);
  }

  @Override
  public void remove(@NonNull String extensionPoint, @NonNull String code) {
    valueTemplate.delete(ExtensionMetadataKeys.metadataKey(extensionPoint, code));
    indexTemplate.opsForSet().remove(ExtensionMetadataKeys.indexKey(extensionPoint), code);
  }

  @Override
  @Nullable
  public ExtensionRoutingMetadata get(@NonNull String extensionPoint, @NonNull String code) {
    return valueTemplate.opsForValue().get(ExtensionMetadataKeys.metadataKey(extensionPoint, code));
  }

  @Override
  @NonNull
  public Map<String, ExtensionRoutingMetadata> getByExtensionPoint(@NonNull String extensionPoint) {
    Set<String> codes =
        indexTemplate.opsForSet().members(ExtensionMetadataKeys.indexKey(extensionPoint));
    if (codes == null || codes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, ExtensionRoutingMetadata> result = new HashMap<>();
    for (String code : codes) {
      ExtensionRoutingMetadata meta = get(extensionPoint, code);
      if (meta != null) {
        result.put(code, meta);
      }
    }
    return result;
  }

  @Override
  public void publishRefresh(@NonNull String extensionPoint) {
    indexTemplate.convertAndSend(refreshChannel, extensionPoint);
    log.info(
        "Published extension metadata refresh on channel {} for {}",
        refreshChannel,
        extensionPoint);
  }

  @Override
  public void clearExtensionPoint(@NonNull String extensionPoint) {
    Map<String, ExtensionRoutingMetadata> all = getByExtensionPoint(extensionPoint);
    all.keySet().forEach(code -> remove(extensionPoint, code));
  }
}
