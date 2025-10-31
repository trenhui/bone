package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.extension.ColumnNamingStrategy;
import com.bone.metadata.sdk.extension.DefaultColumnNamingStrategy;
import com.bone.metadata.sdk.extension.handler.EavHandler;
import com.bone.metadata.sdk.extension.handler.JsonHandler;
import com.bone.metadata.sdk.extension.handler.ReservedColumnsHandler;
import com.bone.metadata.sdk.extension.repository.ColumnAllocationRepository;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.dialect.*;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.sql.processor.SqlProcessorFactory;
import com.bone.metadata.sdk.sql.proxy.RepositoryFactoryBean;
import com.bone.metadata.sdk.sql.proxy.RepositoryRegistrar;
import com.bone.metadata.sdk.sql.template.SqlFragmentLoader;
import com.bone.metadata.sdk.sql.template.SqlTemplateLoader;
import com.bone.metadata.sdk.sql.template.TemplateSecurityValidator;
import com.bone.metadata.sdk.sql.template.UnifiedSqlTemplateLoader;
import com.bone.metadata.sdk.sql.template.parser.MyBatisTemplateParser;
import com.bone.metadata.sdk.sql.template.parser.SqlTemplateParser;
import com.bone.metadata.sdk.sql.template.parser.TemplateContentParser;
import com.bone.metadata.sdk.sql.template.parser.YamlTemplateParser;
import com.bone.metadata.sdk.sql.template.provider.AnnotationSourceProvider;
import com.bone.metadata.sdk.sql.template.provider.ClasspathSourceProvider;
import com.bone.metadata.sdk.sql.template.provider.ClasspathYamlSourceProvider;
import com.bone.metadata.sdk.sql.template.provider.TemplateSourceProvider;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(SqlConfigProperties.class)
@ConditionalOnClass({RepositoryFactoryBean.class})
@Import(RepositoryRegistrar.class)
public class SqlRepositoryAutoConfiguration {

    private final SqlConfigProperties properties;

    public SqlRepositoryAutoConfiguration(SqlConfigProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ColumnNamingStrategy columnNamingStrategy() {
        return new DefaultColumnNamingStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public ColumnAllocator columnAllocator(ColumnAllocationRepository repo, ColumnNamingStrategy namingStrategy) {
        return new ColumnAllocator(repo,namingStrategy);
    }

    @Bean
    public List<ColumnAllocationDialect> columnAllocationDialects() {
        return Arrays.asList(
                new H2ColumnAllocationDialect(),
                new PostgresColumnAllocationDialect(),
                new MySqlColumnAllocationDialect()
        );
    }


    @Bean
    @ConditionalOnMissingBean
    public ColumnAllocationDialectFactory columnAllocationDialectFactory(List<ColumnAllocationDialect> columnAllocationDialects) {
        return new ColumnAllocationDialectFactory(columnAllocationDialects);
    }



    @Bean
    @ConditionalOnMissingBean
    public DatabaseDialect databaseDialect() {
        return new DatabaseDialect();
    }


    @Bean
    @ConditionalOnMissingBean
    public SqlBuilder SqlBuilder(MetadataService metadataService,
                                 DatabaseDialect dialect) {
        return new SqlBuilder(metadataService,dialect);
    }


    @Bean
    @ConditionalOnMissingBean
    public SqlExecutor sqlExecutor(NamedParameterJdbcOperations jdbcOperations,
                                   SqlTemplateLoader sqlTemplateLoader,
                                   SqlProcessorFactory sqlProcessorFactory) {
        return new SqlExecutor(jdbcOperations, sqlTemplateLoader, sqlProcessorFactory, properties);
    }

    @Bean
    public SqlFragmentLoader sqlFragmentLoader(SqlConfigProperties sqlConfigProperties) {
        return new SqlFragmentLoader(sqlConfigProperties);
    }

    @Bean
    @ConditionalOnMissingBean(EavHandler.class)
    public EavHandler eavHandler(NamedParameterJdbcOperations jdbc) {
        return new EavHandler(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(JsonHandler.class)
    public JsonHandler jsonHandler(NamedParameterJdbcOperations jdbc) {
        return new JsonHandler(jdbc);
    }
    @Bean
    @ConditionalOnMissingBean(ReservedColumnsHandler.class)
    public ReservedColumnsHandler reservedColumnsHandler(SqlExecutor sqlExecutor, MetadataService metadataService, DistributedLockUtil distributedLockUtil) {
        return new ReservedColumnsHandler(sqlExecutor,metadataService,distributedLockUtil);
    }

    /**
     * 注册 SQL 模板内容解析器。
     */
    @Bean
    public List<TemplateContentParser> templateContentParsers() {
        return Arrays.asList(
                new SqlTemplateParser(),
                new MyBatisTemplateParser(),
                new YamlTemplateParser()
        );
    }

    /**
     * 注册 SQL 模板内容解析器。
     */
    @Bean
    public List<TemplateSourceProvider> templateSourceProviders(ResourceLoader resourceLoader, SqlConfigProperties config) {
        return Arrays.asList(
                new ClasspathSourceProvider(resourceLoader, config),
                new AnnotationSourceProvider(),
                new ClasspathYamlSourceProvider(resourceLoader, config)
        );
    }


    /**
     * 注册 SQL 模板处理器。
     */
    @Bean
    public SqlProcessorFactory sqlProcessorFactory(SqlConfigProperties config) {
        return new SqlProcessorFactory(config);
    }

    /**
     * 注册 SQL 模板安全验证器。
     */
    @Bean
    public TemplateSecurityValidator templateSecurityValidator(SqlConfigProperties config) {
        return new TemplateSecurityValidator(config);
    }

    /**
     * 注册 SQL 模板加载器。
     * 增加 Cache 和 ConcurrentHashMap 参数
     */
    @Bean
    public SqlTemplateLoader sqlTemplateLoader(
            SqlConfigProperties config,
            List<TemplateSourceProvider> sourceProviders,
            List<TemplateContentParser> contentParsers,
            TemplateSecurityValidator securityValidator
    ) {
        return new UnifiedSqlTemplateLoader(config, sourceProviders, contentParsers, securityValidator);
    }
}