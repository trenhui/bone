package com.bone.procurement.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 metadata-sdk 扩展仓储（{@code ColumnAllocationRepository} 等）。
 *
 * <p>主应用仅扫描 {@code com.bone.procurement}，SDK 的 {@code @Repository} 不会自动注册；缺此扫描时 {@code
 * SqlRepositoryAutoConfiguration} 造不出 {@code columnAllocator} ⇒ {@code
 * delegatingMetadataService}（EMBEDDED 模式）初始化失败 ⇒ 上下文整体起不来。与 bone-claim / bone-integration /
 * bone-blueprint / bone-iam / bone-system / bone-masterdata 同构，凡消费 SDK 的模块都须登记此扫描。
 */
@Configuration
@ComponentScan(
    basePackages = {
      "com.bone.metadata.sdk.extension",
      "com.bone.metadata.sdk.extension.repository"
    })
public class MetadataSdkExtensionScanConfiguration {}
