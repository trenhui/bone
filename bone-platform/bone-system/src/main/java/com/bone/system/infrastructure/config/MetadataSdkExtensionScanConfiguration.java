package com.bone.system.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 metadata-sdk 扩展仓储（{@code ColumnAllocationRepository} 等）。
 *
 * <p>主应用仅扫描 {@code com.bone.system}，SDK 的 {@code @Repository} 不会自动注册；
 * 集成测试见 {@code MetadataSdkIntegrationTestConfiguration}。
 */
@Configuration
@ComponentScan(
        basePackages = {
            "com.bone.metadata.sdk.extension",
            "com.bone.metadata.sdk.extension.repository"
        })
public class MetadataSdkExtensionScanConfiguration {}
