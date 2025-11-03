package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.SqlExecutorAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * QueryBuilder自动配置类，负责在Spring容器中初始化QueryBuilder所需的依赖
 */
@Configuration
@ConditionalOnClass({QueryBuilder.class, SqlExecutorAdapter.class})
public class QueryBuilderAutoConfiguration {

    private final SqlExecutorAdapter sqlExecutorAdapter;

    /**
     * 构造函数，自动注入SqlExecutorAdapter实例和QueryProperties实例
     * @param sqlExecutorAdapter SqlExecutorAdapter实例
     */
    public QueryBuilderAutoConfiguration(SqlExecutorAdapter sqlExecutorAdapter) {
        this.sqlExecutorAdapter = sqlExecutorAdapter;
    }
}