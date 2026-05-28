package com.bone.engine.extension.studio.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(ExtensionStudioProperties.class)
@Import(StudioIdempotencyConfiguration.class)
public class StudioBootstrapConfiguration {}
