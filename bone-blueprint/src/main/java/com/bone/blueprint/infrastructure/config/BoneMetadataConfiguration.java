
package com.bone.blueprint.infrastructure.config;

import com.bone.metadata.sdk.EnableSqlRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")
public class BoneMetadataConfiguration {
}

