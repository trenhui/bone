package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.Criteria;
import com.bone.metadata.sdk.domain.query.QueryParams;
import com.bone.metadata.sdk.domain.query.WhereClause;
import com.bone.metadata.sdk.domain.query.OrderClause;
import com.bone.metadata.sdk.domain.query.PageResult;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.exception.ExceptionHandler;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.processor.SqlProcessor;
import com.bone.metadata.sdk.sql.processor.ProcessedSql;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@ComponentScan(
        basePackages = {
                "com.bone.metadata.sdk.test.repository.proxy",
                "com.bone.metadata.sdk"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\.bone\.metadata\.sdk\.support\.config\..*AutoConfiguration"
        )
)
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
    public SqlTemplateLoader sqlTemplateLoader() {
        return new SqlTemplateLoader() {
            @Override
            public SqlTemplate loadTemplate(String statementId) {
                return null;
            }
        };
    }
    
    @Bean
    public ExceptionHandler exceptionHandler() {
        return new ExceptionHandler() {
            @Override
            public RuntimeException handleException(Exception e) {
                return new RuntimeException(e);
            }
            
            public static ExceptionHandler getInstance() {
                return new ExceptionHandler() {
                    @Override
                    public RuntimeException handleException(Exception e) {
                        return new RuntimeException(e);
                    }
                };
            }
        };
    }
    
    @Bean
    public DistributedLockUtil distributedLockUtil() {
        return new DistributedLockUtil() {
            @Override
            public void lock(String key) {
                // 空实现
            }
            
            @Override
            public void unlock(String key) {
                // 空实现
            }
        };
    }

    @Bean
    public SqlProcessorFactory sqlProcessorFactory() {
        // 返回一个简单的SqlProcessorFactory实现或模拟对象
        return new SqlProcessorFactory() {
            @Override
            public SqlProcessor createProcessor(String templateId) {
                return new DefaultSqlProcessor();
            }
        };
    }

    @Bean
    public SqlConfigProperties sqlConfigProperties() {
        // 返回一个基本的SqlConfigProperties实例
        return new SqlConfigProperties();
    }

    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations, SqlTemplateLoader sqlTemplateLoader, SqlProcessorFactory sqlProcessorFactory, SqlConfigProperties sqlConfigProperties) {
        // 直接使用构造方法创建实例，而不是反射
        return new SqlExecutor(jdbcOperations, sqlTemplateLoader, sqlProcessorFactory, sqlConfigProperties);
    }

    @Bean
    public MetadataService metadataService() {
        // 返回一个简单的MetadataService模拟实现
        return new MetadataService() {
            @Override
            public TableMetadata getTableMetadata(Class<?> entityClass) {
                // 返回一个基本的TableMetadata对象
                return new TableMetadata(entityClass);
            }
            
            // 实现其他必要的方法
        };
    }
    
    @Bean
    public DatabaseDialect databaseDialect() {
        // 返回一个简单的DatabaseDialect模拟实现
        return new DatabaseDialect() {
            @Override
            public String getDialectName() {
                return "H2";
            }
            
            // 实现其他必要的方法
        };
    }
    
    @Bean
    public SqlBuilder sqlBuilder(MetadataService metadataService, DatabaseDialect databaseDialect) {
        // 返回一个测试用的SqlBuilder实现
        return new SqlBuilder(metadataService, databaseDialect) {
            // 重写@PostConstruct方法中初始化的构建器，使用简单实现
            @Override
            public void init() {
                // 避免依赖实际的构建器实现
            }
            
            // 重写主要方法提供简单实现
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

    @Bean
    public ExtensionCoordinator extensionCoordinator() {
        // 返回一个简单的ExtensionCoordinator实现或模拟对象
        return new ExtensionCoordinator() {
            // 实现必要的方法
        };
    }
    
    // 提供DefaultSqlProcessor的简单实现
    private static class DefaultSqlProcessor implements SqlProcessor {
        @Override
        public ProcessedSql process(String template, Map<String, Object> parameters) {
            return new ProcessedSql(template, parameters);
        }
    }

    // 移除对已删除类的Bean定义
}