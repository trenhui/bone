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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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

  @Bean
  @ConditionalOnBean(RedisConnectionFactory.class)
  @ConditionalOnMissingBean(ExtensionMetadataStore.class)
    public ExtensionMetadataStore studioRedisMetadataStore(
            @Qualifier(ExtensionMetadataRedisConfiguration.METADATA_REDIS_TEMPLATE_BEAN)
                    RedisTemplate<String, ExtensionRoutingMetadata> metadataRedisTemplate,
            @Qualifier(ExtensionMetadataRedisConfiguration.METADATA_INDEX_REDIS_TEMPLATE_BEAN)
                    StringRedisTemplate metadataIndexRedisTemplate,
            ExtensionStudioProperties properties) {
        String channel = resolveRefreshChannel(properties);
        log.info("Studio runtime-sync: RedisExtensionMetadataStore, channel={}", channel);
        return new RedisExtensionMetadataStore(metadataRedisTemplate, metadataIndexRedisTemplate, channel);
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
