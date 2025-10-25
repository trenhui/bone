package com.bone.metadata.sdk.test.config;

import org.mockito.Mockito;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.domain.exception.ExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QueryBuilder测试配置类
 */
@TestConfiguration
@ComponentScan(
        basePackages = {
                "com.bone.metadata.sdk.test.repository.proxy"
        },
        // 更严格地控制扫描范围，只扫描测试需要的特定包
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = ".*AutoConfiguration"
        )
)
public class QueryBuilderTestConfig {
    
    @Autowired
    private ExceptionHandler exceptionHandler;
    
    /**
     * 在应用启动时设置QueryBuilder的静态异常处理器
     */
    // 初始化方法，在Bean创建后自动调用
    public void init() {
        try {
            // 设置QueryBuilder的静态异常处理器
            com.bone.metadata.sdk.query.dsl.QueryBuilder.setExceptionHandler(exceptionHandler);
            System.out.println("Successfully set exception handler in QueryBuilderTestConfig");
        } catch (Exception e) {
            System.err.println("Failed to set exception handler in QueryBuilderTestConfig: " + e.getMessage());
            // 即使设置失败，也要继续初始化，避免ApplicationContext加载失败
        }
    }

    /**
     * 配置嵌入式数据库
     */
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .addScript("classpath:data.sql")
                .build();
    }
    
    /**
     * 配置JdbcTemplate
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    /**
     * 配置NamedParameterJdbcOperations
     */
    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
    
    /**
     * 配置SqlConfigProperties
     */
    @Bean
    public SqlConfigProperties sqlConfigProperties() {
        return Mockito.mock(SqlConfigProperties.class);
    }
    
    /**
     * 配置SqlTemplateLoader
     */
    @Bean
    public SqlTemplateLoader sqlTemplateLoader() {
        return Mockito.mock(SqlTemplateLoader.class);
    }
    
    /**
     * 配置SqlProcessorFactory
     */
    @Bean
    public SqlProcessorFactory sqlProcessorFactory() {
        return Mockito.mock(SqlProcessorFactory.class);
    }
    
    /**
     * 配置SqlExecutor
     */
    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations,
                                 SqlTemplateLoader sqlTemplateLoader,
                                 SqlProcessorFactory sqlProcessorFactory,
                                 SqlConfigProperties sqlConfigProperties) {
        return Mockito.mock(SqlExecutor.class);
    }
    
    /**
     * 配置ExceptionHandler，使用单例模式
     */
    @Bean
    public ExceptionHandler exceptionHandler() {
        return ExceptionHandler.getInstance();
    }
    
    /**
     * 配置ExtensionCoordinator
     */
    @Bean
    public ExtensionCoordinator extensionCoordinator() {
        // 使用Spring应用上下文创建ExtensionCoordinator实例
        return Mockito.mock(ExtensionCoordinator.class);
    }
    
    /**
     * 配置MetadataService，确保实现所有必要的泛型方法
     */
    @Bean
    public MetadataService metadataService() {
        MetadataService mockService = Mockito.mock(MetadataService.class);
        // 配置getTableMetadata方法，使用正确的泛型签名
        Mockito.when(mockService.getTableMetadata(Mockito.<Class<?>>any())).thenAnswer(invocation -> {
            Class<?> entityClass = invocation.getArgument(0);
            // 返回一个简单的TableMetadata实例，包含表名
            return new TableMetadata(entityClass.getSimpleName(), Collections.emptyList());
        });
        return mockService;
    }
    
    /**
     * 配置DatabaseDialect
     */
    @Bean
    public DatabaseDialect databaseDialect() {
        return Mockito.mock(DatabaseDialect.class);
    }
    
    /**
     * 配置SqlBuilder
     */
    @Bean
    public SqlBuilder sqlBuilder(MetadataService metadataService, DatabaseDialect databaseDialect) {
        return Mockito.mock(SqlBuilder.class);
    }
}