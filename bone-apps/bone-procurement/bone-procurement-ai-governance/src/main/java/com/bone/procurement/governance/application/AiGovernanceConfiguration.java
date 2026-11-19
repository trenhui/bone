package com.bone.procurement.governance.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册治理能力开关属性绑定；具体 bean / 端点由 {@link ConditionalOnAiGovernanceEnabled} 条件控制。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiGovernanceProperties.class)
public class AiGovernanceConfiguration {}
