package com.bone.masterdata.infrastructure.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 metadata-sdk 扩展仓储（{@code ColumnAllocationRepository} 等）。
 *
 * <p>主应用仅扫描 {@code com.bone.masterdata}，SDK 的 {@code @Repository} 不会自动注册； 与 bone-iam / bone-system
 * 同构。
 */
@Configuration
@ComponentScan(
    basePackages = {
      "com.bone.metadata.sdk.extension",
      "com.bone.metadata.sdk.extension.repository"
    })
public class MetadataSdkExtensionScanConfiguration {}
