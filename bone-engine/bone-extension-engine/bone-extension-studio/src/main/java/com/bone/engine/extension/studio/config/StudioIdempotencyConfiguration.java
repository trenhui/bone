package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.domain.gateway.StudioIdempotencyStore;
import com.bone.engine.extension.studio.infrastructure.idempotency.InMemoryStudioIdempotencyStore;
import com.bone.engine.extension.studio.infrastructure.idempotency.RedisStudioIdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class StudioIdempotencyConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "bone.extension.studio.idempotency",
            name = "backend",
            havingValue = "redis")
    @ConditionalOnBean(StringRedisTemplate.class)
    public StudioIdempotencyStore redisStudioIdempotencyStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ExtensionStudioProperties properties) {
        return new RedisStudioIdempotencyStore(
                redisTemplate,
                objectMapper,
                properties.getIdempotency().getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean(StudioIdempotencyStore.class)
    public StudioIdempotencyStore inMemoryStudioIdempotencyStore() {
        return new InMemoryStudioIdempotencyStore();
    }
}
