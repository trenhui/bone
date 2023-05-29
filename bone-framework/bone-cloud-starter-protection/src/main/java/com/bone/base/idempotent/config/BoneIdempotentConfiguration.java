package com.bone.base.idempotent.config;

import com.bone.base.idempotent.core.aop.IdempotentAspect;
import com.bone.base.idempotent.core.keyresolver.IdempotentKeyResolver;
import com.bone.base.idempotent.core.keyresolver.impl.DefaultIdempotentKeyResolver;
import com.bone.base.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver;
import com.bone.base.idempotent.core.redis.IdempotentRedisDAO;
import com.bone.base.redis.config.BoneRedisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(BoneRedisAutoConfiguration.class)
public class BoneIdempotentConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect(List<IdempotentKeyResolver> keyResolvers, IdempotentRedisDAO idempotentRedisDAO) {
        return new IdempotentAspect(keyResolvers, idempotentRedisDAO);
    }

    @Bean
    public IdempotentRedisDAO idempotentRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentRedisDAO(stringRedisTemplate);
    }

    // ========== 各种 IdempotentKeyResolver Bean ==========

    @Bean
    public DefaultIdempotentKeyResolver defaultIdempotentKeyResolver() {
        return new DefaultIdempotentKeyResolver();
    }

    @Bean
    public ExpressionIdempotentKeyResolver expressionIdempotentKeyResolver() {
        return new ExpressionIdempotentKeyResolver();
    }

}
