package com.bone.engine.extension.support.config;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import com.bone.engine.extension.support.sync.ExtensionMetadataKeys;
import com.bone.engine.extension.support.sync.ExtensionMetadataStore;
import com.bone.engine.extension.support.sync.InMemoryExtensionMetadataStore;
import com.bone.engine.extension.support.sync.MetadataOverlayExtensionRepository;
import com.bone.engine.extension.support.sync.RedisExtensionMetadataStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

/** Studio 控制面与运行时数据面同步（Redis 元数据 + Pub/Sub 刷新）。 */
@Configuration
@ConditionalOnProperty(prefix = "bone.extension.sync", name = "enabled", havingValue = "true")
@Slf4j
public class ExtensionSyncConfiguration {

  @Bean
  @ConditionalOnBean(name = ExtensionMetadataRedisConfiguration.METADATA_REDIS_TEMPLATE_BEAN)
  @ConditionalOnMissingBean(ExtensionMetadataStore.class)
  public ExtensionMetadataStore redisExtensionMetadataStore(
      @Qualifier(ExtensionMetadataRedisConfiguration.METADATA_REDIS_TEMPLATE_BEAN)
          RedisTemplate<String, ExtensionRoutingMetadata> metadataRedisTemplate,
      @Qualifier(ExtensionMetadataRedisConfiguration.METADATA_INDEX_REDIS_TEMPLATE_BEAN)
          StringRedisTemplate metadataIndexRedisTemplate,
      ExtensionProperties properties) {
    String channel = resolveRefreshChannel(properties);
    log.info("Using RedisExtensionMetadataStore, refresh channel={}", channel);
    return new RedisExtensionMetadataStore(
        metadataRedisTemplate, metadataIndexRedisTemplate, channel);
  }

  @Bean
  @ConditionalOnMissingBean(ExtensionMetadataStore.class)
  public ExtensionMetadataStore inMemoryExtensionMetadataStore() {
    log.info("Using InMemoryExtensionMetadataStore (no Redis metadata template)");
    return new InMemoryExtensionMetadataStore();
  }

  @Bean
  @Primary
  public ExtensionRepository syncingExtensionRepository(
      @Qualifier("localExtensionRepository") ExtensionRepository localExtensionRepository,
      ExtensionMetadataStore metadataStore,
      ExpressionEvaluator expressionEvaluator) {
    log.info(
        "Primary ExtensionRepository: MetadataOverlay over {}",
        localExtensionRepository.getClass().getSimpleName());
    return new MetadataOverlayExtensionRepository(
        localExtensionRepository, metadataStore, expressionEvaluator);
  }

  @Bean
  @ConditionalOnBean(RedisConnectionFactory.class)
  public RedisMessageListenerContainer extensionMetadataListenerContainer(
      RedisConnectionFactory connectionFactory,
      ExtensionPointRouter extensionPointRouter,
      ExtensionProperties properties) {
    String channel = resolveRefreshChannel(properties);
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(
        (message, pattern) -> {
          String extensionPoint = new String(message.getBody());
          log.info(
              "Metadata refresh received on {} for extension point: {}", channel, extensionPoint);
          clearRouterCache(extensionPoint, extensionPointRouter);
        },
        new ChannelTopic(channel));
    return container;
  }

  @Bean
  public InMemoryRefreshBridge inMemoryRefreshBridge(
      ExtensionMetadataStore metadataStore, ExtensionPointRouter extensionPointRouter) {
    InMemoryRefreshBridge bridge = new InMemoryRefreshBridge(extensionPointRouter);
    if (metadataStore instanceof InMemoryExtensionMetadataStore inMemory) {
      inMemory.addRefreshListener(bridge::onRefresh);
    }
    return bridge;
  }

  static final class InMemoryRefreshBridge {
    private final ExtensionPointRouter extensionPointRouter;

    InMemoryRefreshBridge(ExtensionPointRouter extensionPointRouter) {
      this.extensionPointRouter = extensionPointRouter;
    }

    void onRefresh(String extensionPoint) {
      clearRouterCache(extensionPoint, extensionPointRouter);
    }
  }

  private static String resolveRefreshChannel(ExtensionProperties properties) {
    String configured = properties.getSync().getRefreshChannel();
    return StringUtils.hasText(configured) ? configured : ExtensionMetadataKeys.REFRESH_CHANNEL;
  }

  private static void clearRouterCache(
      String extensionPointName, ExtensionPointRouter extensionPointRouter) {
    try {
      extensionPointRouter.clearCache(Class.forName(extensionPointName));
    } catch (ClassNotFoundException e) {
      log.warn("Skip router cache clear, class not found: {}", extensionPointName);
    }
  }
}
