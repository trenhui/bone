package com.bone.iam.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 metadata-sdk 扩展仓储，供 EMBEDDED 模式 {@code ColumnAllocator} 使用。
 *
 * <p>与 bone-system 同构；主应用 {@code scanBasePackages = com.bone.iam} 不包含 SDK 包。
 */
@Configuration
@ComponentScan(
        basePackages = {
            "com.bone.metadata.sdk.extension",
            "com.bone.metadata.sdk.extension.repository"
        })
public class MetadataSdkExtensionScanConfiguration {}
