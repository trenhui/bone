package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.sql.dialect.ColumnAllocationDialect;
import com.bone.metadata.sdk.sql.dialect.ColumnAllocationDialectFactory;
import com.bone.metadata.sdk.sql.dialect.H2ColumnAllocationDialect;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

@TestConfiguration
@SpringBootConfiguration
@ComponentScan(basePackages = {
        "com.bone.metadata.sdk.query",
        "com.bone.metadata.sdk.extension",
        "com.bone.metadata.sdk.test.repository",
        "com.bone.metadata.sdk.test.domain"
})
public class PermissionTestConfig {
    
    // 配置嵌入式数据库
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .addScript("classpath:data.sql")
                .build();
    }
    
    // 配置JdbcTemplate
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    // 直接配置NamedParameterJdbcOperations
    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
    
    // 配置columnAllocationDialects bean
    @Bean
    public List<ColumnAllocationDialect> columnAllocationDialects() {
        return Arrays.asList(
                new H2ColumnAllocationDialect()
        );
    }
    
    // 配置ColumnAllocationDialectFactory bean
    @Bean
    public ColumnAllocationDialectFactory columnAllocationDialectFactory(List<ColumnAllocationDialect> columnAllocationDialects) {
        return new ColumnAllocationDialectFactory(columnAllocationDialects);
    }
}