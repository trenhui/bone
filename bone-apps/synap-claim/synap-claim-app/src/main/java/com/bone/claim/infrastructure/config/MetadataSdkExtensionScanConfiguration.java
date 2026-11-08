package com.bone.claim.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 metadata-sdk 扩展仓储（{@code ColumnAllocationRepository} 等）。
 *
 * <p>主应用仅扫描 {@code com.bone.claim}，SDK 的 {@code @Repository} 不会自动注册； 缺此扫描时 {@code
 * SqlRepositoryAutoConfiguration} 造不出 {@code columnAllocator} ⇒ {@code
 * delegatingMetadataService}（EMBEDDED 模式）初始化失败 ⇒ 上下文整体起不来。 与 bone-integration / bone-blueprint /
 * bone-iam / bone-system / bone-masterdata 同构，凡消费 SDK 的模块都须登记此扫描。
 *
 * <p>幂等：与宿主自带同名扫描对同一批 {@code @Repository} 重复注册时，Spring 对「同名同类」的 bean 定义判为兼容并跳过。
 */
@Configuration
@ComponentScan(
    basePackages = {
      "com.bone.metadata.sdk.extension",
      "com.bone.metadata.sdk.extension.repository"
    })
public class MetadataSdkExtensionScanConfiguration {}
