package com.bone.metadata.engine.starter.platform;

import com.bone.metadata.engine.platform.MetadataPlatformBridge;
import com.bone.metadata.engine.platform.NoopMetadataPlatformBridge;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** META-ENG-01：注册平台元数据桥接 Bean（SDK 可用时用 SdkMetadataPlatformBridge）。 */
@Configuration
public class MetadataEnginePlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetadataPlatformBridge.class)
    @ConditionalOnClass(QueryBuilder.class)
    public MetadataPlatformBridge sdkMetadataPlatformBridge() {
        return new SdkMetadataPlatformBridge();
    }

    @Bean
    @ConditionalOnMissingBean(MetadataPlatformBridge.class)
    public MetadataPlatformBridge noopMetadataPlatformBridge() {
        return new NoopMetadataPlatformBridge();
    }
}
