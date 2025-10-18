package com.bone.metadata.sdk.test.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * QueryBuilder测试配置类
 */
@TestConfiguration
@Import({
        JdbcTemplateAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@ComponentScan(basePackages = {
        "com.bone.metadata.sdk.query",
        "com.bone.metadata.sdk.test"
})
public class QueryBuilderTestConfig {

    /**
     * 配置嵌入式数据库
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }
}