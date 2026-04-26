package com.bone.tpa.claim.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * 数据库配置
 */
@Configuration
@EnableJdbcRepositories(basePackages = "com.bone.tpa.domain.repository")
public class DatabaseConfig {
    // 数据库配置，如有需要
}
