package com.bone.metadata.sdk.test.config;

import javax.annotation.PostConstruct;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.Criteria;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.exception.ExceptionHandler;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
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
    @PostConstruct
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
        return new SqlConfigProperties();
    }
    
    /**
     * 配置SqlTemplateLoader
     */
    @Bean
    public SqlTemplateLoader sqlTemplateLoader() {
        return new SqlTemplateLoader() {
            @Override
            public SqlTemplate loadTemplate(String statementId) {
                return null;
            }
        };
    }
    
    /**
     * 配置SqlProcessorFactory
     */
    @Bean
    public SqlProcessorFactory sqlProcessorFactory() {
        return new SqlProcessorFactory(sqlConfigProperties()) {
            @Override
            public Object createSqlProcessor(String type) {
                return null;
            }
        };
    }
    
    /**
     * 配置SqlExecutor
     */
    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations,
                                 SqlTemplateLoader sqlTemplateLoader,
                                 SqlProcessorFactory sqlProcessorFactory,
                                 SqlConfigProperties sqlConfigProperties) {
        return new SqlExecutor(jdbcOperations, sqlTemplateLoader, sqlProcessorFactory, sqlConfigProperties) {
            // 重写方法以避免实际的SQL执行
        };
    }
    
    /**
     * 配置ExceptionHandler
     */
    @Bean
    public ExceptionHandler exceptionHandler() {
        return new ExceptionHandler() {
            @Override
            public RuntimeException handleException(Exception e) {
                return new RuntimeException(e);
            }
            
            @Override
            public RuntimeException handleException(Exception e, String message) {
                return new RuntimeException(message, e);
            }
            
            @Override
            public void logException(Exception e) {
                System.out.println("Exception logged: " + e.getMessage());
            }
        };
    }
    
    /**
     * 配置ExtensionCoordinator
     */
    @Bean
    public ExtensionCoordinator extensionCoordinator() {
        return new ExtensionCoordinator() {
            // 实现必要的方法
        };
    }
    
    /**
     * 配置MetadataService
     */
    @Bean
    public MetadataService metadataService() {
        return new MetadataService() {
            @Override
            public TableMetadata getTableMetadata(Class<?> entityClass) {
                return new TableMetadata(entityClass);
            }
            // 实现其他必要的方法
        };
    }
    
    /**
     * 配置DatabaseDialect
     */
    @Bean
    public DatabaseDialect databaseDialect() {
        return new DatabaseDialect() {
            @Override
            public String getDialectName() {
                return "H2";
            }
            // 实现其他必要的方法
        };
    }
    
    /**
     * 配置SqlBuilder
     */
    @Bean
    public SqlBuilder sqlBuilder(MetadataService metadataService, DatabaseDialect databaseDialect) {
        return new SqlBuilder(metadataService, databaseDialect) {
            @Override
            public void init() {
                // 避免依赖实际的构建器实现
            }
            
            @Override
            public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c) {
                return new CompiledQuery("SELECT * FROM " + cls.getSimpleName().toLowerCase(), Collections.emptyMap());
            }
            
            @Override
            public BatchCompiledQuery buildBatchInsert(Class<?> cls, List<?> list) {
                return new BatchCompiledQuery("INSERT INTO " + cls.getSimpleName().toLowerCase() + " VALUES (:id, :name)", 
                        list.stream().map(obj -> Collections.emptyMap()).toList());
            }
            
            @Override
            public CompiledQuery buildCount(Class<?> cls, Criteria<?> c) {
                return new CompiledQuery("SELECT COUNT(*) FROM " + cls.getSimpleName().toLowerCase(), Collections.emptyMap());
            }
            
            @Override
            public CompiledQuery buildDelete(Class<?> cls, Criteria<?> c) {
                return new CompiledQuery("DELETE FROM " + cls.getSimpleName().toLowerCase(), Collections.emptyMap());
            }
            
            @Override
            public CompiledQuery buildUpdate(Class<?> cls, Map<String, Object> updateValues, Criteria<?> c) {
                return new CompiledQuery("UPDATE " + cls.getSimpleName().toLowerCase() + " SET id = :id", Collections.emptyMap());
            }
        };
    }
}