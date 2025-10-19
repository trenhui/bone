package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * QueryBuilder自动配置类，负责在Spring容器中初始化QueryBuilder所需的依赖
 */
@Configuration
@ConditionalOnClass({QueryBuilder.class, SqlExecutor.class})
public class QueryBuilderAutoConfiguration {

    private final SqlExecutor sqlExecutor;

    /**
     * 构造函数，自动注入SqlExecutor实例
     * @param sqlExecutor SqlExecutor实例
     */
    public QueryBuilderAutoConfiguration(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * 在配置类初始化后，将SqlExecutor实例注入到QueryBuilder
     */
    @PostConstruct
    public void init() {
        QueryBuilder.setSqlExecutor(sqlExecutor);
    }
}