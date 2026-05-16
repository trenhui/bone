package com.bone.engine.extension.studio.config;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata SDK 表持久化（{@code ext_studio_extension_point} / {@code ext_studio_extension_impl}）。
 */
@Configuration
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "metadata")
@EnableSqlRepositories(basePackages = "com.bone.engine.extension.studio.infrastructure.persistence.repository")
@Slf4j
public class StudioMetadataPersistenceConfiguration {

    public StudioMetadataPersistenceConfiguration() {
        log.info("Studio persistence mode: metadata (Bone Metadata SDK)");
    }
}
