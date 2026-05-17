package com.bone.engine.extension.studio.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExtensionStudioProperties.class)
public class StudioPropertiesConfiguration {}
