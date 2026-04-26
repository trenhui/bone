package com.bone.lowcode.infra.infrastructure.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Autowired
    RedisConfigProperties redisConfigProperties;

    @Bean
    public RedissonClient redissonClient() {
        // 1. 创建配置文件
        Config config = new Config();

        // 2. 设置单节点服务器配置
        config.useSingleServer()
                .setAddress("redis://" + redisConfigProperties.getHost() + ":" + redisConfigProperties.getPort());
//                .setAddress("redis://localhost:6379");

        // 3. 如果Redis设置了密码，这里需要设置密码
        if (StringUtils.hasText(redisConfigProperties.getPassword())) {
            config.useSingleServer().setPassword(redisConfigProperties.getPassword());
        }

        // 4. 创建RedissonClient实例并返回
        return Redisson.create(config);
    }
}
