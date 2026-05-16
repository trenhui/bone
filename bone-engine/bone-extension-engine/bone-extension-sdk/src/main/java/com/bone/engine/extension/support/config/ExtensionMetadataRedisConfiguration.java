package com.bone.engine.extension.support.config;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** 扩展元数据专用 RedisTemplate（Jackson 序列化，避免 JDK 序列化兼容问题）。 */
@Configuration
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class ExtensionMetadataRedisConfiguration {

    public static final String METADATA_REDIS_TEMPLATE_BEAN = "extensionMetadataRedisTemplate";
    public static final String METADATA_INDEX_REDIS_TEMPLATE_BEAN = "extensionMetadataIndexRedisTemplate";

    @Bean(name = METADATA_INDEX_REDIS_TEMPLATE_BEAN)
    @ConditionalOnMissingBean(name = METADATA_INDEX_REDIS_TEMPLATE_BEAN)
    public StringRedisTemplate extensionMetadataIndexRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = METADATA_REDIS_TEMPLATE_BEAN)
  @ConditionalOnMissingBean(name = METADATA_REDIS_TEMPLATE_BEAN)
  public RedisTemplate<String, ExtensionRoutingMetadata> extensionMetadataRedisTemplate(
      RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
    RedisTemplate<String, ExtensionRoutingMetadata> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    StringRedisSerializer keySerializer = new StringRedisSerializer();
    Jackson2JsonRedisSerializer<ExtensionRoutingMetadata> valueSerializer =
        new Jackson2JsonRedisSerializer<>(objectMapper, ExtensionRoutingMetadata.class);
    template.setKeySerializer(keySerializer);
    template.setHashKeySerializer(keySerializer);
    template.setValueSerializer(valueSerializer);
    template.setHashValueSerializer(valueSerializer);
    template.afterPropertiesSet();
    return template;
  }
}
