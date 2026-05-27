package com.bone.blueprint.infrastructure.config;

import com.bone.blueprint.application.config.OrderOutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderOutboxProperties.class)
public class OrderOutboxConfiguration {
}
