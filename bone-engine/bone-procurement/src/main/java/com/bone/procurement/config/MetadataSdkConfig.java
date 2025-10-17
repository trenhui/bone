package com.bone.procurement.config;

import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * bone-metadata-sdk配置类
 * 启用必要的组件并确保正确的包扫描
 */
@Configuration
@Import(SqlRepositoryAutoConfiguration.class)
@ComponentScan({
    "com.bone.metadata.sdk.extension",
    "com.bone.metadata.sdk.metadata",
    "com.bone.metadata.sdk.sql"
})
public class MetadataSdkConfig {
    // 配置类，用于启用bone-metadata-sdk的组件
}