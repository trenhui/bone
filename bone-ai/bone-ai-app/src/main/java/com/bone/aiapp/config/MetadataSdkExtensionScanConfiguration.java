package com.bone.aiapp.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 metadata-sdk 扩展仓储（{@code ColumnAllocationRepository} 等）。
 *
 * <p>主应用仅扫描 {@code com.bone.aiapp}，SDK 的 {@code @Repository} 不会自动注册； 与 bone-blueprint / bone-iam /
 * bone-system / bone-masterdata / bone-integration 同构。
 *
 * <p>2026-10-21 补齐：缺此注册时 SDK 的 {@code SqlRepositoryAutoConfiguration} 造不出 {@code
 * columnAllocator}（依赖 {@code ColumnAllocationRepository}）→ {@code delegatingMetadataService} 初始化失败
 * → 整个上下文起不来，聚合应用根本无法启动。
 */
@Configuration
@ComponentScan(
    basePackages = {
      "com.bone.metadata.sdk.extension",
      "com.bone.metadata.sdk.extension.repository"
    })
public class MetadataSdkExtensionScanConfiguration {}
