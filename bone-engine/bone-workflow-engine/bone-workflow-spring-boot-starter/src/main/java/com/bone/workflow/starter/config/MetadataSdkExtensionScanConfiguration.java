package com.bone.workflow.starter.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 注册 metadata-sdk 扩展仓储（{@code ColumnAllocationRepository} 等）。
 *
 * <p><b>为什么 starter 必须自带这一条</b>：本 starter 的平台桥接装配（{@code WorkflowBoneIntegrationAutoConfiguration}）
 * 依赖 metadata 运行时（{@code RuntimeActionApplicationService}）与 SDK 的 {@code SqlExecutor}；宿主默认只扫描自身包，
 * SDK 的扩展 {@code @Repository} 不会自动注册 ⇒ {@code SqlRepositoryAutoConfiguration} 造不出 {@code
 * columnAllocator} （依赖 {@code ColumnAllocationRepository}）⇒ {@code delegatingMetadataService} 初始化失败
 * ⇒ 上下文整体起不来。 与 bone-blueprint / bone-iam / bone-system / bone-ai-server 同构，凡消费 SDK 的模块都须登记此扫描。
 *
 * <p>幂等：与宿主自带同名扫描对同一批 {@code @Repository} 重复注册时，Spring 对「同名同类」的 bean 定义判为兼容并跳过， 不会抛 {@code
 * ConflictingBeanDefinitionException}。
 */
@AutoConfiguration
@ComponentScan(
    basePackages = {
      "com.bone.metadata.sdk.extension",
      "com.bone.metadata.sdk.extension.repository"
    })
public class MetadataSdkExtensionScanConfiguration {}
