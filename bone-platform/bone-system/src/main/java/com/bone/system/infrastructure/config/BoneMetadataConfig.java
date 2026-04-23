package com.bone.system.infrastructure.config;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableSqlRepositories(basePackages = "com.bone.system.domain.repository")
public class BoneMetadataConfig {
}
