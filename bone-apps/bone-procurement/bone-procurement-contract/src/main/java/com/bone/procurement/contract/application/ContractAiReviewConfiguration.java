package com.bone.procurement.contract.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册能力开关属性绑定；具体 bean / 端点由 {@link ConditionalOnContractAiReviewEnabled} 条件控制。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContractAiReviewProperties.class)
public class ContractAiReviewConfiguration {}
