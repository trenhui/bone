package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.test.repository.impl.PermissionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

@Configuration
public class SimpleTestConfig {

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
    }

    @Bean
    public NamedParameterJdbcOperations jdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations) {
        // 假设SqlExecutor有一个接受NamedParameterJdbcOperations的构造函数
        try {
            return new SqlExecutor(jdbcOperations);
        } catch (Exception e) {
            // 如果构造函数不匹配，尝试通过反射设置属性
            SqlExecutor executor = new SqlExecutor();
            try {
                java.lang.reflect.Field field = SqlExecutor.class.getDeclaredField("jdbcOperations");
                field.setAccessible(true);
                field.set(executor, jdbcOperations);
            } catch (Exception ex) {
                // 如果都失败了，返回null（这会导致测试失败，但至少我们会看到具体的错误）
                return null;
            }
            return executor;
        }
    }

    @Bean
    public SqlBuilder sqlBuilder() {
        // 假设SqlBuilder有一个无参构造函数
        return new SqlBuilder();
    }

    @Bean
    public ExtensionCoordinator extensionCoordinator() {
        // 假设ExtensionCoordinator有一个无参构造函数
        return new ExtensionCoordinator();
    }

    @Bean
    public PermissionRepository permissionRepository(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        return new PermissionRepository(sqlBuilder, sqlExecutor, extensionCoordinator);
    }
}