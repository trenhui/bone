package com.bone.engine.extension.core.cache;

import com.bone.engine.extension.support.config.ExtensionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 缓存配置类
 * <p>
 * 配置本地缓存和分布式缓存的Bean
 * </p>
 *
 * @since 1.0.0
 */
@Configuration
public class CacheConfiguration {

    /**
     * 缓存管理器Bean
     * <p>
     * 当分布式缓存启用且RedisTemplate存在时，使用分布式缓存
     * </p>
     */
    @Bean
    @ConditionalOnBean(RedisTemplate.class)
    @ConditionalOnProperty(name = "bone.extension.cache.distributed-enabled", havingValue = "true")
    public CacheManager cacheManager(ExtensionProperties properties, RedisTemplate<String, Object> redisTemplate) {
        return new CacheManager(properties, redisTemplate);
    }

    /**
     * 缓存管理器Bean（仅本地缓存）
     * <p>
     * 当分布式缓存未启用或RedisTemplate不存在时，仅使用本地缓存
     * </p>
     */
    @Bean
    @ConditionalOnProperty(name = "bone.extension.cache.distributed-enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager localCacheManager(ExtensionProperties properties) {
        return new CacheManager(properties, null);
    }
}
