package com.bone.claim.infrastructure.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * bone-ai-rag 装配登记（门控开时）：宿主只扫 com.bone.claim，bone-ai-rag 的仓储与 自动配置须显式扫描（与
 * MetadataSdkExtensionScanConfiguration 同构，幂等——同名同类 bean 定义 Spring 判为兼容并跳过）。门控关时不扫描，类路径行为与现状一致。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "bone.claim.knowledge.rag.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.bone.airag")
public class ClaimRagScanConfiguration {}
