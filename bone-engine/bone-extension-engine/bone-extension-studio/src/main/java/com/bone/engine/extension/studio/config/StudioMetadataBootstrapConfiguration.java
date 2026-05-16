package com.bone.engine.extension.studio.config;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.support.config.MetadataSdkProperties;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Studio 控制面 Metadata 引导：不启动完整嵌入式字段分配栈，仅满足 SQL Repository / SqlBuilder 依赖。
 */
@Configuration
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "metadata")
@EnableConfigurationProperties(MetadataSdkProperties.class)
@ComponentScan(
        basePackages = {
            "com.bone.metadata.sdk.extension",
            "com.bone.metadata.sdk.extension.repository"
        })
public class StudioMetadataBootstrapConfiguration {

    @Bean
    public MetadataSdkContext metadataSdkContext(
            MetadataSdkProperties props, DataSourceProperties dsProps) {
        return new MetadataSdkContext(props, dsProps);
    }

    @Bean
    @Primary
    public MetadataService studioMetadataService() {
        return new MetadataService() {
            @Override
            public List<FieldMetadata> findExtensionFields(AllocationContext context) {
                return Collections.emptyList();
            }

            @Override
            public List<FieldMetadata> findExtensionFieldsByNames(
                    AllocationContext context, List<String> logicalNames) {
                return Collections.emptyList();
            }

            @Override
            public List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields) {
                return Collections.emptyList();
            }

            @Override
            public boolean isHealthy() {
                return true;
            }
        };
    }

    @Bean
    public DistributedLockUtil distributedLockUtil() {
        return new DistributedLockUtil();
    }
}
