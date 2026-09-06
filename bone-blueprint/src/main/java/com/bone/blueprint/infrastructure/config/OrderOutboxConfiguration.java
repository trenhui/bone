package com.bone.blueprint.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderOutboxProperties.class)
public class OrderOutboxConfiguration {}
