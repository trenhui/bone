package com.bone.metadata.sdk.test.config;

import org.mockito.Mockito;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.domain.exception.ExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.context.annotation.ComponentScan;
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
                pattern = "com\\.bone\\.metadata\\.sdk\\.support\\.config\\..*AutoConfiguration"
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
        return Mockito.mock(SqlTemplateLoader.class);
    }
    
    @Bean
    public ExceptionHandler exceptionHandler() {
        return ExceptionHandler.getInstance();
    }
    
    @Bean
    public DistributedLockUtil distributedLockUtil() {
        return Mockito.mock(DistributedLockUtil.class);
    }

    @Bean
    public SqlProcessorFactory sqlProcessorFactory() {
        SqlProcessorFactory mock = Mockito.mock(SqlProcessorFactory.class);
        return mock;
    }

    @Bean
    public SqlConfigProperties sqlConfigProperties() {
        return Mockito.mock(SqlConfigProperties.class);
    }

    @Bean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations, SqlConfigProperties sqlConfigProperties, SqlProcessorFactory sqlProcessorFactory) {
        return Mockito.mock(SqlExecutor.class);
    }

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
    
    @Bean
    public DatabaseDialect databaseDialect() {
        return Mockito.mock(DatabaseDialect.class);
    }
    
    @Bean
    public SqlBuilder sqlBuilder(MetadataService metadataService, DatabaseDialect databaseDialect) {
        return Mockito.mock(SqlBuilder.class);
    }

    @Bean
    public ExtensionCoordinator extensionCoordinator() {
        // ExtensionCoordinator需要ApplicationContext参数，使用mock对象模拟
        return Mockito.mock(ExtensionCoordinator.class);
    }
    
    // 提供DefaultSqlProcessor的简单实现
    private static class DefaultSqlProcessor implements SqlProcessor {
        @Override
        public ProcessedSql process(String template, String dialect, Map<String, Object> parameters) {
            return new ProcessedSql(template, parameters);
        }
        
        public ProcessedSql process(String template, Map<String, Object> parameters) {
            return new ProcessedSql(template, parameters);
        }
    }

    // 移除对已删除类的Bean定义
}