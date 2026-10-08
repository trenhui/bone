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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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

  /**
   * Redis 相关 Bean【移入嵌套配置类】（2026-10-07 修正）。
   *
   * <p><b>为什么原来那样写是错的</b>：{@code @ConditionalOnClass} 原先直接打在 {@code redisExtensionMetadataStore} 这个
   * {@code @Bean} 方法上。Spring Boot 官方文档（reference「Developing Your Own Auto-configuration」· Class
   * Conditions）明确写道：该机制「does not apply the same way to {@code @Bean} methods where typically <b>the
   * return type is the target of the condition</b>」—— 也就是说打在 {@code @Bean} 方法上时，条件按**方法的返回类型**（这里是
   * {@code ExtensionMetadataStore}，恒在 classpath 上）判断，**而不是**按你写的 {@code RedisTemplate} 判断 ⇒
   * 该条件形同虚设。
   *
   * <p><b>实证（2026-10-07，FilteredClassLoader 实测）</b>：把 {@code RedisTemplate} 从 classpath 剔除后，
   * 容器仍然在解析该 {@code @Bean} 方法签名时才失败（而不是被条件提前跳过） ⇒ 条件确实没生效。
   *
   * <p><b>官方推荐的写法</b>：把条件放到<b>独立的嵌套 {@code @Configuration} 类</b>上， 让 JVM 在类级判断（此时不加载 {@code @Bean}
   * 方法）⇒条件真正生效。 契约见 {@code ExtensionSyncConfigurationConditionalTest}。
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RedisTemplate.class)
  static class RedisBackedExtensionMetadataConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExtensionMetadataStore.class)
    ExtensionMetadataStore redisExtensionMetadataStore(
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

    /**
     * Redis 订阅监听容器：同样必须待在 {@code @ConditionalOnClass(RedisTemplate.class)} 保护的嵌套类里 —— 它的方法签名含
     * {@code RedisConnectionFactory}（Redis 类）， 条件失效时 JVM 会在解析签名时才报缺类。
     */
    @Bean
    RedisMessageListenerContainer extensionMetadataListenerContainer(
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
