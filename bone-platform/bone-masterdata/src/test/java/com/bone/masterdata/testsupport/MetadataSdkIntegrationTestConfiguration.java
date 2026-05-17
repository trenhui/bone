package com.bone.masterdata.testsupport;

import com.bone.metadata.sdk.extension.repository.FieldMetadataRepository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/**
 * 集成测试用 metadata-sdk 扩展仓储注册。
 */
@Configuration
@ComponentScan(basePackages = {"com.bone.metadata.sdk.extension", "com.bone.metadata.sdk.extension.repository"})
public class MetadataSdkIntegrationTestConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FieldMetadataRepository fieldMetadataRepository(NamedParameterJdbcOperations jdbc) {
        return new FieldMetadataRepository(jdbc);
    }

    @Bean
    public SmartInitializingSingleton queryBuilderInitializer(SqlExecutor sqlExecutor) {
        return () -> QueryBuilder.initialize(sqlExecutor);
    }
}
