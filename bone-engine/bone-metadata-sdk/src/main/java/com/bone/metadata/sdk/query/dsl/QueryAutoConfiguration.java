package com.bone.metadata.sdk.query.dsl;

import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DSL查询自动配置类 - 提供Spring Boot自动配置支持
 */
@Configuration
@EnableConfigurationProperties(QueryBuilder.QueryProperties.class)
public class QueryAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(QueryAutoConfiguration.class);

    /**
     * 初始化QueryBuilder
     *
     * @param sqlExecutorAdapter SQL执行器适配器
     * @param queryProperties    查询属性配置
     * @return QueryBuilder初始化后的配置对象
     */
    @Bean
    @ConditionalOnMissingBean(SqlExecutorAdapter.class)
    public SqlExecutorAdapter sqlExecutorAdapter(SqlExecutor sqlExecutor) {
        logger.info("Creating default SqlExecutorAdapter");
        // 返回一个默认的SQL执行器适配器实现，实际项目中应该替换为真实的实现
        return new SqlExecutorAdapter(sqlExecutor);
    }

    /**
     * 初始化QueryBuilder
     *
     * @param sqlExecutorAdapter SQL执行器适配器
     * @param queryProperties    查询属性配置
     */
    @Bean
    public void queryBuilderInitializer(SqlExecutorAdapter sqlExecutorAdapter, QueryBuilder.QueryProperties queryProperties) {
        logger.info("Initializing QueryBuilder with SqlExecutorAdapter");
        QueryBuilder.initialize(sqlExecutorAdapter, queryProperties);
    }
}