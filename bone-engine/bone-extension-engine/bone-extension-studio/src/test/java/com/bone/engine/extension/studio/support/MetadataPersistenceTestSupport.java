package com.bone.engine.extension.studio.support;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.support.config.MetadataSdkProperties;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.bone.metadata.sdk.support.util.DistributedLockUtil;
import java.util.Collections;
import java.util.List;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Studio Metadata 集成测试支撑：不启动嵌入式元数据服务，仅满足 SqlBuilder / 仓储代理依赖。
 */
@Configuration
@EnableConfigurationProperties({MetadataSdkProperties.class, SqlConfigProperties.class})
public class MetadataPersistenceTestSupport {

    @Bean
    public MetadataSdkContext metadataSdkContext(
            MetadataSdkProperties props, DataSourceProperties dsProps) {
        return new MetadataSdkContext(props, dsProps);
    }

    @Bean
    @Primary
    public MetadataService studioTestMetadataService() {
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
    @Primary
    public ColumnAllocator studioTestColumnAllocator() {
        return Mockito.mock(ColumnAllocator.class);
    }

    @Bean
    public DistributedLockUtil distributedLockUtil() {
        return new DistributedLockUtil();
    }
}
