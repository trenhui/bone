package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService;
import com.bone.engine.extension.support.config.ExtensionMetadataRedisConfiguration;
import com.bone.engine.extension.support.sync.ExtensionMetadataKeys;
import com.bone.engine.extension.support.sync.ExtensionMetadataStore;
import com.bone.engine.extension.support.sync.InMemoryExtensionMetadataStore;
import com.bone.engine.extension.support.sync.RedisExtensionMetadataStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/** Studio 侧运行时元数据推送：无 Redis 时使用内存存储（单进程联调）。 */
@Configuration
@ConditionalOnProperty(
    prefix = "bone.extension.studio.runtime-sync",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(ExtensionStudioProperties.class)
@Import(ExtensionMetadataRedisConfiguration.class)
@Slf4j
public class ExtensionStudioSyncConfiguration {

  /**
   * Redis 相关 Bean【移入嵌套配置类】（2026-10-07 修正，与 ExtensionSyncConfiguration 同因）。
   *
   * <p>{@code @ConditionalOnClass} 原先直接打在 {@code @Bean} 方法上。Spring Boot 官方文档 （reference「Developing
   * Your Own Auto-configuration」· Class Conditions）明确写道：该机制 「does not apply the same way to
   * {@code @Bean} methods where typically <b>the return type is the target of the condition</b>」⇒
   * 条件按方法返回类型（{@code ExtensionMetadataStore}， 恒在 classpath）判断，<b>而不是</b>按 {@code RedisTemplate} 判断
   * ⇒ 条件形同虚设。
   *
   * <p><b>实证</b>：{@code ExtensionSyncConfiguration} 上的同类写法用 {@code FilteredClassLoader}
   * 实测确认条件未生效（Redis 缺失时根因链仍点名 RedisTemplate）， 改为本嵌套类写法后条件才真正生效。
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RedisTemplate.class)
  static class RedisBackedStudioMetadataConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExtensionMetadataStore.class)
    ExtensionMetadataStore studioRedisMetadataStore(
        @Qualifier(ExtensionMetadataRedisConfiguration.METADATA_REDIS_TEMPLATE_BEAN)
            RedisTemplate<String, ExtensionRoutingMetadata> metadataRedisTemplate,
        @Qualifier(ExtensionMetadataRedisConfiguration.METADATA_INDEX_REDIS_TEMPLATE_BEAN)
            StringRedisTemplate metadataIndexRedisTemplate,
        ExtensionStudioProperties properties) {
      String channel = resolveRefreshChannel(properties);
      log.info("Studio runtime-sync: RedisExtensionMetadataStore, channel={}", channel);
      return new RedisExtensionMetadataStore(
          metadataRedisTemplate, metadataIndexRedisTemplate, channel);
    }
  }

  @Bean
  @ConditionalOnMissingBean(ExtensionMetadataStore.class)
  public ExtensionMetadataStore studioInMemoryMetadataStore() {
    log.info("Studio runtime-sync: InMemoryExtensionMetadataStore");
    return new InMemoryExtensionMetadataStore();
  }

  @Bean
  public RuntimeExtensionSyncService runtimeExtensionSyncService(
      ExtensionMetadataStore metadataStore, ExtPointRepository extPointRepository) {
    return new RuntimeExtensionSyncService(metadataStore, extPointRepository);
  }

  private static String resolveRefreshChannel(ExtensionStudioProperties properties) {
    String configured = properties.getRuntimeSync().getRefreshChannel();
    return StringUtils.hasText(configured) ? configured : ExtensionMetadataKeys.REFRESH_CHANNEL;
  }
}
