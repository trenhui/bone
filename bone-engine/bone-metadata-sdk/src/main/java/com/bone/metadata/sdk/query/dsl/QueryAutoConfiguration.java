package com.bone.metadata.sdk.query.dsl;

import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DSL查询自动配置类 - 提供Spring Boot自动配置支持
 */
@Configuration
@ConditionalOnClass({QueryBuilder.class, SqlExecutorAdapter.class})
public class QueryAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QueryAutoConfiguration.class);

    /**
     * 创建并初始化SqlExecutorAdapter
     */
    @Bean
    @ConditionalOnMissingBean(SqlExecutorAdapter.class)
    public SqlExecutorAdapter sqlExecutorAdapter(SqlExecutor sqlExecutor) {
        logger.info("Creating and initializing default SqlExecutorAdapter");
        SqlExecutorAdapter adapter = new SqlExecutorAdapter(sqlExecutor);

        // 初始化QueryBuilder
        QueryBuilder.initialize(adapter);
        logger.info("QueryBuilder initialized successfully");

        return adapter;
    }
}